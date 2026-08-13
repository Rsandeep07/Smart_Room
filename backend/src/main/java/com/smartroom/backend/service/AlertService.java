package com.smartroom.backend.service;

import com.smartroom.backend.domain.Alert;
import com.smartroom.backend.repository.AlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Raising, resolving and dismissing the cold-room alert of Section 9.
 */
@Service
public class AlertService {

    public static final String COLD_ROOM_AC_RUNTIME = "COLD_ROOM_AC_RUNTIME";
    public static final String SEVERITY_WARNING = "WARNING";

    private final AlertRepository repository;
    private final EventLogService eventLog;

    public AlertService(AlertRepository repository, EventLogService eventLog) {
        this.repository = repository;
        this.eventLog = eventLog;
    }

    /**
     * Raises the cold-room alert, at most once per AC cycle.
     *
     * <p>The monitor evaluates the condition every 60 seconds, so without the
     * {@code acStatusId} guard a room left cold for two hours would produce 120
     * identical banners. Scoping to the AC interval means one alert per qualifying
     * cycle: switching the AC off and on again is a new cycle and can alert again.
     *
     * @return the alert if one was raised, empty if this cycle already alerted
     */
    @Transactional
    public Optional<Alert> raiseColdRoomAlert(String roomId, String message, Double temperature,
                                              Duration acRuntime, Long acStatusId) {
        if (repository.existsByRoomIdAndAlertTypeAndAcStatusId(roomId, COLD_ROOM_AC_RUNTIME, acStatusId)) {
            return Optional.empty();
        }
        Alert alert = repository.save(Alert.builder()
                .roomId(roomId)
                .alertType(COLD_ROOM_AC_RUNTIME)
                .severity(SEVERITY_WARNING)
                .message(message)
                .temperature(temperature == null ? null
                        : BigDecimal.valueOf(temperature).setScale(2, RoundingMode.HALF_UP))
                .acRuntimeSeconds(acRuntime == null ? null : acRuntime.getSeconds())
                .acStatusId(acStatusId)
                .createdAt(LocalDateTime.now())
                .build());
        eventLog.warning(roomId, EventLogService.ALERT_RAISED, "Alert triggered - " + message);
        return Optional.of(alert);
    }

    /**
     * Clears any active alert once its condition no longer holds.
     *
     * <p>The alert asks the receptionist to switch the AC off. Leaving the banner up
     * after they have done so would train them to ignore it, so a resolved condition
     * closes the alert on its own. The row is kept with its timestamps for the
     * history and the report.
     */
    @Transactional
    public void resolveActive(String roomId, String alertType, String reason) {
        Optional<Alert> active =
                repository.findFirstByRoomIdAndAlertTypeAndAcknowledgedAtIsNullOrderByCreatedAtDesc(roomId, alertType);
        if (active.isEmpty()) {
            return;
        }
        Alert alert = active.get();
        alert.setAcknowledgedAt(LocalDateTime.now());
        repository.save(alert);
        eventLog.info(roomId, EventLogService.ALERT_RESOLVED, "Alert cleared - " + reason);
    }

    /** Dismissal from the dashboard banner. */
    @Transactional
    public Optional<Alert> acknowledge(Long alertId) {
        return repository.findById(alertId).map(alert -> {
            if (alert.getAcknowledgedAt() == null) {
                alert.setAcknowledgedAt(LocalDateTime.now());
                repository.save(alert);
                eventLog.info(alert.getRoomId(), EventLogService.ALERT_ACKNOWLEDGED,
                        "Alert dismissed by operator - " + alert.getMessage());
            }
            return alert;
        });
    }

    @Transactional(readOnly = true)
    public Optional<Alert> activeAlert(String roomId) {
        return repository.findFirstByRoomIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(roomId);
    }

    @Transactional(readOnly = true)
    public List<Alert> recent(String roomId, int limit) {
        return repository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, limit));
    }
}
