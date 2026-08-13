package com.smartroom.backend.web.dto;

import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.AcStatusSource;

import java.time.LocalDateTime;

/**
 * Body of {@code GET /api/room/{roomId}/status} - everything the dashboard of
 * Section 14 renders, in one request.
 *
 * <p>Deliberately one call rather than six. The dashboard polls every five seconds
 * (build plan Step 6.6), and six polled endpoints would let the tiles disagree with
 * each other mid-refresh - a person count from one instant beside an AC runtime from
 * another. One response is one consistent snapshot.
 *
 * @param systemStatus                  NORMAL, ALERT or DEGRADED - drives the header pill
 * @param sensorOnline                  false when the ESP32 has gone quiet for longer than
 *                                      {@code stale-reading-timeout}
 * @param visionOnline                  false when the Python vision service has gone quiet
 * @param recommendationBase            Step 1 occupancy term, exposed so the dashboard can
 *                                      show the engine's working
 * @param recommendationAdjustment      Step 2 temperature correction, in Kelvin
 * @param pendingRecommendedTemperature set when hysteresis is holding a change back
 * @param acDuration                    HH:MM:SS, as the tile displays it
 * @param manualOverrideExpiresAt       set while a dashboard override outranks the vent probe
 */
public record RoomStatusResponse(
        String roomId,
        LocalDateTime serverTime,
        String systemStatus,

        Double temperature,
        LocalDateTime temperatureAt,
        Double humidity,
        LocalDateTime humidityAt,
        Double ventTemperature,
        LocalDateTime ventTemperatureAt,
        Double ventDelta,
        Integer personCount,
        LocalDateTime personCountAt,
        boolean sensorOnline,
        boolean visionOnline,

        Integer recommendedTemperature,
        String recommendationMessage,
        String recommendationReason,
        Integer recommendationBase,
        int recommendationAdjustment,
        LocalDateTime recommendationUpdatedAt,
        Integer pendingRecommendedTemperature,
        LocalDateTime recommendationHoldUntil,

        AcState acStatus,
        AcStatusSource acStatusSource,
        String acMode,
        LocalDateTime acSince,
        long acDurationSeconds,
        String acDuration,
        LocalDateTime manualOverrideExpiresAt,

        AlertResponse alert,
        Thresholds thresholds
) {

    /**
     * The configured rule parameters, echoed to the dashboard.
     *
     * <p>Sent rather than duplicated in the frontend so that the interface can label a
     * threshold ("below the 21 °C cold threshold") without a second copy of the
     * numbers drifting out of step with the backend's.
     */
    public record Thresholds(
            double targetTemperature,
            double coldThreshold,
            long acRuntimeLimitMinutes,
            double ventDeltaThreshold,
            long hysteresisMinutes,
            int recommendationMin,
            int recommendationMax
    ) {
    }
}
