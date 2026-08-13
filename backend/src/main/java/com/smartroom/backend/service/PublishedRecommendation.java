package com.smartroom.backend.service;

import java.time.LocalDateTime;

/**
 * The recommendation the dashboard is currently showing, together with what the
 * engine would say right now if hysteresis were not holding it back.
 *
 * <p>Section 21 Step 4 exists because a recommendation that flickers as the person
 * count wobbles by one or two stops being trusted. The consequence is that the
 * displayed value legitimately lags the live one, so the lag is made explicit
 * rather than hidden: {@code pendingTemperature} and {@code holdUntil} let the
 * dashboard say "23 °C, changing to 24 °C in 4 min" instead of appearing stuck.
 *
 * @param recommendedTemperature the published setpoint, or null before the first
 *                               person count arrives
 * @param baseTemperature        Step 1 occupancy-only term behind the published value
 * @param adjustment             Step 2 correction behind the published value
 * @param pendingTemperature     what the engine wants now, if it differs and is being
 *                               held back; null when the published value is current
 * @param holdUntil              when the pending change becomes publishable; null when
 *                               nothing is pending
 */
public record PublishedRecommendation(
        Integer recommendedTemperature,
        String message,
        String reason,
        Integer baseTemperature,
        int adjustment,
        LocalDateTime publishedAt,
        Integer pendingTemperature,
        LocalDateTime holdUntil
) {
}
