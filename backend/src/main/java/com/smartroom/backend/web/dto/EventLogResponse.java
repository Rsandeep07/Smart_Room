package com.smartroom.backend.web.dto;

import com.smartroom.backend.domain.EventLog;

import java.time.LocalDateTime;

/** One row of the dashboard's Logs panel. */
public record EventLogResponse(
        Long id,
        String level,
        String eventType,
        String message,
        LocalDateTime createdAt
) {
    public static EventLogResponse from(EventLog event) {
        return new EventLogResponse(
                event.getId(),
                event.getLevel(),
                event.getEventType(),
                event.getMessage(),
                event.getCreatedAt());
    }
}
