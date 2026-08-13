package com.smartroom.backend.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Body of {@code POST /api/detection} - a person count from the Python vision
 * service.
 *
 * <p>{@code personCount} is expected to be the median over the reporting window,
 * not a single frame (Section 20.4). The backend cannot verify that, so the
 * contract is stated here and enforced in the vision service.
 *
 * @param roomId      room this count belongs to
 * @param personCount median person count over the reporting window
 * @param source      optional provenance, e.g. {@code yolov8n-960}, for the calibration
 *                    record of build plan Step 7
 * @param recordedAt  optional producer timestamp; server clock is used when absent
 */
public record DetectionRequest(

        @NotBlank(message = "roomId is required")
        @Size(max = 32, message = "roomId must be at most 32 characters")
        String roomId,

        @NotNull(message = "personCount is required")
        @Min(value = 0, message = "personCount cannot be negative")
        @Max(value = 500, message = "personCount is outside the plausible range for a classroom")
        Integer personCount,

        @Size(max = 64, message = "source must be at most 64 characters")
        String source,

        LocalDateTime recordedAt
) {
}
