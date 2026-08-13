package com.smartroom.backend.web.dto;

import com.smartroom.backend.domain.Alert;

import java.time.LocalDateTime;

/** An alert as the dashboard banner needs it (Section 14). */
public record AlertResponse(
        Long id,
        String alertType,
        String severity,
        String message,
        Double temperature,
        Long acRuntimeSeconds,
        LocalDateTime createdAt,
        LocalDateTime acknowledgedAt
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getTemperature() == null ? null : alert.getTemperature().doubleValue(),
                alert.getAcRuntimeSeconds(),
                alert.getCreatedAt(),
                alert.getAcknowledgedAt());
    }
}
