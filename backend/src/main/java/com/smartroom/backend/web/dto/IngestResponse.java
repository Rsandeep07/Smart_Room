package com.smartroom.backend.web.dto;

import java.time.LocalDateTime;

/**
 * Reply to the two ingest endpoints.
 *
 * <p>It carries the resulting recommendation back to the producer. The ESP32 does not
 * need it, but it makes the endpoints self-checking from curl during build plan Step 4
 * - one POST shows both that the row was stored and what the engine concluded - and it
 * leaves room for an OLED status readout on the device later (Section 22.3).
 *
 * @param id                      primary key of the stored {@code room_data} row
 * @param recommendedTemperature  currently published setpoint, null before the first person count
 * @param alertActive             whether an unacknowledged alert exists for the room
 */
public record IngestResponse(
        String status,
        Long id,
        String roomId,
        LocalDateTime recordedAt,
        Integer recommendedTemperature,
        boolean alertActive
) {
}
