package com.smartroom.backend.engine;

/**
 * The output of one evaluation of the Section 21 rules.
 *
 * <p>{@code baseTemperature} and {@code adjustment} are carried separately from
 * {@code recommendedTemperature} so the dashboard and the project report can show
 * <em>why</em> a setpoint was recommended, not only what it was. Section 20.5's
 * criticism of the original rules is that they were indefensible under
 * examination; keeping the working visible is the answer to that.
 *
 * @param baseTemperature       Step 1 result - the occupancy-only setpoint
 * @param adjustment            Step 2 result - the measured-temperature correction, in Kelvin
 * @param recommendedTemperature Step 2 result after clamping, or null when there is
 *                              not enough telemetry to recommend anything
 * @param message               operator-facing sentence for the recommendation card
 * @param reason                short subtitle for the recommended-temperature tile
 * @param alert                 Step 3 result
 * @param alertMessage          operator-facing alert sentence, null when {@code alert} is false
 */
public record Decision(
        Integer baseTemperature,
        int adjustment,
        Integer recommendedTemperature,
        String message,
        String reason,
        boolean alert,
        String alertMessage
) {
}
