package com.smartroom.backend.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Body of {@code GET /api/room/{roomId}/history} - the series behind the
 * Temperature History and Person Count History charts.
 *
 * <p>Each measurement is its own series with its own timestamps rather than one
 * array of wide rows. Temperature arrives every 30 seconds from the ESP32 and person
 * counts every 10 seconds from the vision service, so wide rows would be mostly nulls
 * and the charts would have to filter them back out.
 *
 * <p>Samples are averaged into fixed-width buckets chosen from the requested range,
 * so a 24-hour view returns on the order of a hundred points rather than the ten
 * thousand raw samples the two producers generate in a day.
 *
 * @param bucketMinutes width of the averaging bucket in minutes, always at least 1
 */
public record HistoryResponse(
        String roomId,
        int hours,
        LocalDateTime from,
        LocalDateTime to,
        int bucketMinutes,
        List<Point> temperature,
        List<Point> humidity,
        List<Point> personCount
) {

    /** One plotted sample. {@code value} is null for a bucket with no data - a gap, not a zero. */
    public record Point(LocalDateTime t, Double value) {
    }
}
