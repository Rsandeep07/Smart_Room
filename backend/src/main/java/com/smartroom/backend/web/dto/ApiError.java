package com.smartroom.backend.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Uniform error body, so the ESP32 firmware and the vision service can parse one shape. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path, List<String> details) {
        return new ApiError(LocalDateTime.now(), status, error, message, path, details);
    }
}
