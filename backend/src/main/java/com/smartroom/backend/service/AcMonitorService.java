package com.smartroom.backend.service;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.AcStatus;
import com.smartroom.backend.domain.AcStatusSource;
import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.repository.AcStatusRepository;
import com.smartroom.backend.repository.RoomDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives AC ON/OFF state and maintains the {@code ac_status} intervals
 * (build plan Step 4.5).
 *
 * <p>The primary source is the supply-vent probe of Section 20.3 Option C: the AC is
 * running when the vent is more than {@code vent-delta-threshold} colder than the
 * room. This needs no mains contact, no electrician and no building permission, and
 * unlike Option A it cannot be forgotten.
 *
 * <p>Option A survives as a dashboard override with a time-to-live. Section 20.3's
 * objection to manual entry is that a receptionist forgets to change it back, so an
 * override here expires and the probe resumes on its own.
 */
@Service
public class AcMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AcMonitorService.class);

    private final RoomDataRepository roomDataRepository;
    private final AcStatusRepository acStatusRepository;
    private final RoomStateService roomStateService;
    private final RecommendationService recommendationService;
    private final EventLogService eventLog;
    private final SmartRoomProperties properties;

    /** Active dashboard overrides, keyed by room. Intentionally not persisted: an override should not survive a restart. */
    private final Map<String, ManualOverride> overrides = new ConcurrentHashMap<>();

    /** Rooms currently reported as having stale sensor data, so the warning is logged on transition only. */
    private final Map<String, Boolean> staleRooms = new ConcurrentHashMap<>();

    public AcMonitorService(RoomDataRepository roomDataRepository,
                           AcStatusRepository acStatusRepository,
                           RoomStateService roomStateService,
                           RecommendationService recommendationService,
                           EventLogService eventLog,
                           SmartRoomProperties properties) {
        this.roomDataRepository = roomDataRepository;
        this.acStatusRepository = acStatusRepository;
        this.roomStateService = roomStateService;
        this.recommendationService = recommendationService;
        this.eventLog = eventLog;
        this.properties = properties;
    }

    /**
     * The 60-second monitor tick.
     *
     * <p>Each room is handled in its own try/catch. A single room whose sensor is
     * feeding garbage must not take the scheduler down and stop monitoring every other
     * room - the build plan's Step 8 gate is a full working day unattended.
     */
    @Scheduled(
            fixedDelayString = "${smartroom.ac.monitor-interval-ms:60000}",
            initialDelayString = "${smartroom.ac.monitor-initial-delay-ms:10000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (String roomId : roomStateService.knownRoomIds()) {
            try {
                reconcile(roomId, now);
                recommendationService.evaluate(roomId);
            } catch (Exception e) {
                log.error("AC monitor tick failed for room {}", roomId, e);
            }
        }
    }

    /** Brings {@code ac_status} into line with the currently derived state. */
    @Transactional
    public void reconcile(String roomId, LocalDateTime now) {
        Optional<Resolution> resolved = resolveState(roomId, now);
        if (resolved.isEmpty()) {
            // Nothing trustworthy to decide on. Hold the last known interval rather
            // than inventing an OFF transition, which would reset the runtime timer
            // and lose the alert condition.
            return;
        }
        applyState(roomId, resolved.get(), now);
    }

    /**
     * Works out whether the AC is running.
     *
     * @return empty when neither an override nor a fresh vent/room sample pair is
     *         available, i.e. when the honest answer is "unknown"
     */
    private Optional<Resolution> resolveState(String roomId, LocalDateTime now) {
        ManualOverride override = overrides.get(roomId);
        if (override != null) {
            if (now.isBefore(override.expiresAt())) {
                return Optional.of(new Resolution(override.state(), AcStatusSource.MANUAL,
                        "manual override, expires " + override.expiresAt().toLocalTime().withNano(0)));
            }
            overrides.remove(roomId);
            eventLog.info(roomId, EventLogService.SYSTEM,
                    "Manual AC override expired - vent probe resumed");
        }

        Optional<RoomData> sample = roomDataRepository
                .findFirstByRoomIdAndTemperatureIsNotNullAndVentTemperatureIsNotNullOrderByRecordedAtDesc(roomId);
        if (sample.isEmpty()) {
            return Optional.empty();
        }

        RoomData reading = sample.get();
        LocalDateTime staleBefore = now.minus(properties.getAc().getStaleReadingTimeout());
        if (reading.getRecordedAt().isBefore(staleBefore)) {
            if (staleRooms.putIfAbsent(roomId, Boolean.TRUE) == null) {
                eventLog.warning(roomId, EventLogService.SENSOR_STALE,
                        "No vent/room sample since %s - AC state held at last known value"
                                .formatted(reading.getRecordedAt().toLocalTime().withNano(0)));
            }
            return Optional.empty();
        }
        if (staleRooms.remove(roomId) != null) {
            eventLog.info(roomId, EventLogService.SYSTEM, "Sensor telemetry resumed");
        }

        double roomTemp = reading.getTemperature().doubleValue();
        double ventTemp = reading.getVentTemperature().doubleValue();
        double delta = roomTemp - ventTemp;
        AcState state = delta > properties.getAc().getVentDeltaThreshold() ? AcState.ON : AcState.OFF;

        return Optional.of(new Resolution(state, AcStatusSource.VENT_PROBE,
                String.format(Locale.ROOT, "room %.1f °C, vent %.1f °C, delta %.1f K (threshold %.1f K)",
                        roomTemp, ventTemp, delta, properties.getAc().getVentDeltaThreshold())));
    }

    /** Closes the open interval and opens a new one, but only on a genuine state change. */
    private void applyState(String roomId, Resolution resolution, LocalDateTime now) {
        Optional<AcStatus> openInterval =
                acStatusRepository.findFirstByRoomIdAndEndTimeIsNullOrderByStartTimeDesc(roomId);

        if (openInterval.isPresent() && openInterval.get().getStatus() == resolution.state()) {
            return;
        }

        openInterval.ifPresent(interval -> {
            interval.setEndTime(now);
            acStatusRepository.save(interval);
        });

        acStatusRepository.save(AcStatus.builder()
                .roomId(roomId)
                .status(resolution.state())
                .source(resolution.source())
                .startTime(now)
                .build());

        eventLog.info(roomId, EventLogService.AC_STATE_CHANGE,
                "AC turned %s (%s: %s)".formatted(
                        resolution.state(), resolution.source(), resolution.explanation()));
    }

    /**
     * Section 10 Option A - the retained dashboard override.
     *
     * <p>Applied immediately and then re-evaluated so the dashboard reflects it on the
     * next poll rather than at the next monitor tick.
     */
    @Transactional
    public void applyManualOverride(String roomId, AcState state, String note) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plus(properties.getAc().getManualOverrideTtl());
        overrides.put(roomId, new ManualOverride(state, expiry));

        eventLog.info(roomId, EventLogService.SYSTEM,
                "AC set to %s manually from the dashboard%s (override expires %s)".formatted(
                        state,
                        note == null || note.isBlank() ? "" : " - " + note.trim(),
                        expiry.toLocalTime().withNano(0)));

        reconcile(roomId, now);
        recommendationService.evaluate(roomId);
    }

    /** Hands control back to the vent probe before the override would expire. */
    @Transactional
    public void clearManualOverride(String roomId) {
        if (overrides.remove(roomId) != null) {
            eventLog.info(roomId, EventLogService.SYSTEM,
                    "Manual AC override cleared - vent probe resumed");
            LocalDateTime now = LocalDateTime.now();
            reconcile(roomId, now);
            recommendationService.evaluate(roomId);
        }
    }

    /** When the active override expires, or null if the vent probe is in control. */
    public LocalDateTime manualOverrideExpiry(String roomId) {
        ManualOverride override = overrides.get(roomId);
        if (override == null || !LocalDateTime.now().isBefore(override.expiresAt())) {
            return null;
        }
        return override.expiresAt();
    }

    private record ManualOverride(AcState state, LocalDateTime expiresAt) {
    }

    private record Resolution(AcState state, AcStatusSource source, String explanation) {
    }
}
