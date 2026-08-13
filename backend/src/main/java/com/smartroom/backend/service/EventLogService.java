package com.smartroom.backend.service;

import com.smartroom.backend.domain.EventLog;
import com.smartroom.backend.repository.EventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records operational events to both the application log and {@code event_log}
 * (build plan Step 8.3).
 *
 * <p>Written on state transitions only - AC on/off, a changed recommendation, an
 * alert raised or resolved, a sensor going quiet. Telemetry itself is not logged
 * here; at one sample every 30 seconds that would bury the events that matter
 * under thousands of rows saying nothing happened.
 */
@Service
public class EventLogService {

    /** Event type discriminators. */
    public static final String AC_STATE_CHANGE = "AC_STATE_CHANGE";
    public static final String RECOMMENDATION_CHANGE = "RECOMMENDATION_CHANGE";
    public static final String OCCUPANCY = "OCCUPANCY";
    public static final String ALERT_RAISED = "ALERT_RAISED";
    public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
    public static final String ALERT_ACKNOWLEDGED = "ALERT_ACKNOWLEDGED";
    public static final String SENSOR_STALE = "SENSOR_STALE";
    public static final String SYSTEM = "SYSTEM";

    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARNING = "WARNING";

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final EventLogRepository repository;

    public EventLogService(EventLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventLog info(String roomId, String eventType, String message) {
        log.info("[{}] {} - {}", roomId, eventType, message);
        return save(roomId, LEVEL_INFO, eventType, message);
    }

    @Transactional
    public EventLog warning(String roomId, String eventType, String message) {
        log.warn("[{}] {} - {}", roomId, eventType, message);
        return save(roomId, LEVEL_WARNING, eventType, message);
    }

    private EventLog save(String roomId, String level, String eventType, String message) {
        return repository.save(EventLog.builder()
                .roomId(roomId)
                .level(level)
                .eventType(eventType)
                .message(truncate(message))
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<EventLog> recent(String roomId, int limit) {
        return repository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<EventLog> since(String roomId, LocalDateTime since, int limit) {
        return repository.findByRoomIdAndCreatedAtAfterOrderByCreatedAtDesc(
                roomId, since, PageRequest.of(0, limit));
    }

    /** {@code event_log.message} is VARCHAR(255); a long message is trimmed, not rejected. */
    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 255 ? message : message.substring(0, 252) + "...";
    }
}
