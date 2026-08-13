package com.smartroom.backend.engine;

import com.smartroom.backend.domain.AcState;

import java.time.Duration;

/**
 * The four inputs of Section 21: {@code personCount P, temperature T, acStatus S,
 * acRuntime R}.
 *
 * @param personCount median occupancy over the reporting window, or null if the
 *                    vision service has not reported yet
 * @param temperature room temperature in degrees Celsius, or null if the DHT22 has
 *                    not reported yet
 * @param acState     current AC state, never null (unknown resolves to OFF)
 * @param acRuntime   how long the AC has been in {@code acState}
 */
public record DecisionInput(
        Integer personCount,
        Double temperature,
        AcState acState,
        Duration acRuntime
) {
}
