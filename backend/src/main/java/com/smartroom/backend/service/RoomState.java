package com.smartroom.backend.service;

import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.AcStatus;
import com.smartroom.backend.engine.DecisionInput;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Everything currently known about one room, assembled from the latest non-null
 * reading of each measurement plus the open AC interval.
 *
 * <p>This is the join Section 3 describes - person count, temperature and AC
 * runtime brought together - materialised once so the decision engine and the
 * status endpoint cannot disagree about what "now" looks like.
 *
 * @param acInterval  the open {@code ac_status} row, or null if AC state has never
 *                    been established for this room
 * @param evaluatedAt the single instant this snapshot is taken at; AC runtime is
 *                    measured to it rather than to {@code now()} so that a decision
 *                    and the status shown beside it agree to the second
 */
public record RoomState(
        String roomId,
        Double temperature,
        LocalDateTime temperatureAt,
        Double humidity,
        LocalDateTime humidityAt,
        Double ventTemperature,
        LocalDateTime ventTemperatureAt,
        Integer personCount,
        LocalDateTime personCountAt,
        AcStatus acInterval,
        LocalDateTime evaluatedAt
) {

    /** Unknown AC state resolves to OFF: the alert rule must not fire on a guess. */
    public AcState acState() {
        return acInterval == null ? AcState.OFF : acInterval.getStatus();
    }

    public Duration acRuntime() {
        return acInterval == null ? Duration.ZERO : acInterval.durationAsOf(evaluatedAt);
    }

    public DecisionInput toDecisionInput() {
        return new DecisionInput(personCount, temperature, acState(), acRuntime());
    }

    /** Room minus vent temperature - the quantity AC state is derived from (Section 20.3). */
    public Double ventDelta() {
        if (temperature == null || ventTemperature == null) {
            return null;
        }
        return temperature - ventTemperature;
    }

    public boolean isSensorFresh(Duration timeout) {
        return temperatureAt != null && temperatureAt.isAfter(evaluatedAt.minus(timeout));
    }

    public boolean isVisionFresh(Duration timeout) {
        return personCountAt != null && personCountAt.isAfter(evaluatedAt.minus(timeout));
    }
}
