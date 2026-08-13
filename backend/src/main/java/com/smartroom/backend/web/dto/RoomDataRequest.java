package com.smartroom.backend.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Body of {@code POST /api/room/data} - one sensor sample from the ESP32-CAM.
 *
 * <p>The ranges are sanity bounds, not calibration. A DHT22 that has lost its data
 * line reads back as a wild value or NaN; rejecting those at the edge keeps
 * nonsense out of the history charts and out of the AC-state comparison.
 *
 * @param roomId          room this sample belongs to
 * @param temperature     room air temperature, degrees Celsius (DHT22)
 * @param humidity        relative humidity, percent (DHT22)
 * @param ventTemperature AC supply-vent temperature, degrees Celsius (DS18B20, Section 20.3)
 * @param recordedAt      optional device timestamp; the server clock is used when absent,
 *                        which is also what lets a buffered reading be back-dated after a
 *                        Wi-Fi drop (Section 20.6)
 */
public record RoomDataRequest(

        @NotBlank(message = "roomId is required")
        @Size(max = 32, message = "roomId must be at most 32 characters")
        String roomId,

        @DecimalMin(value = "-40.0", message = "temperature is outside the plausible range")
        @DecimalMax(value = "85.0", message = "temperature is outside the plausible range")
        Double temperature,

        @DecimalMin(value = "0.0", message = "humidity must be between 0 and 100")
        @DecimalMax(value = "100.0", message = "humidity must be between 0 and 100")
        Double humidity,

        @DecimalMin(value = "-40.0", message = "ventTemperature is outside the plausible range")
        @DecimalMax(value = "85.0", message = "ventTemperature is outside the plausible range")
        Double ventTemperature,

        LocalDateTime recordedAt
) {
    /** True when the sample carries no measurement at all - accepted by validation, but useless. */
    public boolean isEmpty() {
        return temperature == null && humidity == null && ventTemperature == null;
    }
}
