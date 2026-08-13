package com.smartroom.backend.service;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.repository.RoomDataRepository;
import com.smartroom.backend.web.dto.DetectionRequest;
import com.smartroom.backend.web.dto.RoomDataRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ingest for the two producers: the ESP32-CAM (temperature, humidity, vent
 * temperature) and the Python vision service (person count).
 *
 * <p>Both write to {@code room_data}. A sample is stored exactly as received; the
 * engine reads the latest non-null value of each measurement rather than expecting
 * any one row to be complete.
 */
@Service
public class TelemetryService {

    /**
     * Occupancy change that is worth an entry in the Logs panel. Below this the
     * count is drifting by a person or two, which Section 20.4 tells us to expect
     * from a single wide-angle camera and which is not an event.
     */
    private static final int OCCUPANCY_LOG_DELTA = 5;

    private final RoomDataRepository roomDataRepository;
    private final EventLogService eventLog;
    private final SmartRoomProperties properties;

    public TelemetryService(RoomDataRepository roomDataRepository,
                           EventLogService eventLog,
                           SmartRoomProperties properties) {
        this.roomDataRepository = roomDataRepository;
        this.eventLog = eventLog;
        this.properties = properties;
    }

    /** POST /api/room/data - a sensor sample from the ESP32-CAM. */
    @Transactional
    public RoomData recordSensorSample(RoomDataRequest request) {
        LocalDateTime recordedAt = request.recordedAt() != null ? request.recordedAt() : LocalDateTime.now();
        return roomDataRepository.save(RoomData.builder()
                .roomId(request.roomId())
                .temperature(scale(request.temperature()))
                .humidity(scale(request.humidity()))
                .ventTemperature(scale(request.ventTemperature()))
                .recordedAt(recordedAt)
                .build());
    }

    /** POST /api/detection - a person count from the vision service. */
    @Transactional
    public RoomData recordDetection(DetectionRequest request) {
        LocalDateTime recordedAt = request.recordedAt() != null ? request.recordedAt() : LocalDateTime.now();

        Optional<RoomData> previous =
                roomDataRepository.findFirstByRoomIdAndPersonCountIsNotNullOrderByRecordedAtDesc(request.roomId());

        RoomData saved = roomDataRepository.save(RoomData.builder()
                .roomId(request.roomId())
                .personCount(request.personCount())
                .recordedAt(recordedAt)
                .build());

        logSignificantOccupancyChange(request, previous);
        return saved;
    }

    /**
     * Records a notable occupancy change in the Logs panel.
     *
     * <p>Skipped entirely for a back-dated sample. Replayed readings - the ESP32's offline
     * buffer after a reconnect, or a bulk history load - belong in the charts, not in the
     * event log: they would arrive as a burst of entries all stamped with the current time,
     * describing changes that happened hours ago, and bury the events that actually
     * matter. The same reasoning keeps them out of the decision engine
     * (see {@link RecommendationService#evaluateForIngest}).
     */
    private void logSignificantOccupancyChange(DetectionRequest request, Optional<RoomData> previous) {
        LocalDateTime freshFrom = LocalDateTime.now().minus(properties.getAc().getStaleReadingTimeout());
        if (request.recordedAt() != null && request.recordedAt().isBefore(freshFrom)) {
            return;
        }

        int count = request.personCount();
        if (previous.isEmpty()) {
            eventLog.info(request.roomId(), EventLogService.OCCUPANCY,
                    "First person count received: %d %s detected".formatted(count, people(count)));
            return;
        }
        int before = previous.get().getPersonCount();
        if (Math.abs(count - before) < OCCUPANCY_LOG_DELTA) {
            return;
        }
        String direction = count > before ? "High occupancy detected" : "Low occupancy detected";
        eventLog.info(request.roomId(), EventLogService.OCCUPANCY,
                "%s (%d %s, was %d)".formatted(direction, count, people(count), before));
    }

    private static String people(int count) {
        return count == 1 ? "person" : "people";
    }

    /** {@code DECIMAL(5,2)} columns: round on the way in so stored and displayed values agree. */
    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
