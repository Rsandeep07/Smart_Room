package com.smartroom.backend.engine;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.AcState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * The Section 21 revised decision engine.
 *
 * <p>Deliberately a pure function of {@link DecisionInput} and configuration: no
 * repository, no clock, no state. Hysteresis (Step 4) and persistence (Step 5) are
 * stateful and live in {@link com.smartroom.backend.service.RecommendationService},
 * which leaves the rules themselves directly unit-testable - which is what makes the
 * calibration work of build plan Step 7 tractable.
 */
@Component
public class DecisionEngine {

    private final SmartRoomProperties.Engine config;

    public DecisionEngine(SmartRoomProperties properties) {
        this.config = properties.getEngine();
    }

    /**
     * Step 1 - base setpoint from occupancy alone (the Section 8 table).
     *
     * <p>Kept verbatim from Section 8 as the base term. It is not a defensible
     * recommendation on its own - that is exactly Section 20.5's objection - which is
     * why {@link #temperatureAdjustment(double)} always runs on top of it.
     */
    public int baseSetpoint(int personCount) {
        if (personCount <= 5) {
            return 26;
        }
        if (personCount <= 15) {
            return 25;
        }
        if (personCount <= 25) {
            return 24;
        }
        if (personCount <= 40) {
            return 23;
        }
        return 22;
    }

    /**
     * Step 2 - correction from the measured room temperature.
     *
     * <p>The band is asymmetric as specified: the dead zone runs from
     * {@code T_target - 1} to {@code T_target + 2}, so a room a degree over target
     * is left alone while a room a degree under target has its setpoint raised. That
     * asymmetry is the point of the rule - Section 20.5's failure case is a room that
     * has been overcooled, so the engine is quicker to back off cooling than to add it.
     */
    public int temperatureAdjustment(double temperature) {
        double target = config.getTargetTemperature();
        if (temperature < target - 1.0) {
            return +1;
        }
        if (temperature > target + 2.0) {
            return -1;
        }
        return 0;
    }

    /** Evaluates Steps 1 to 3. Hysteresis and persistence are the caller's business. */
    public Decision evaluate(DecisionInput input) {
        boolean alert = isColdRoomAlert(input);
        String alertMessage = alert ? coldRoomAlertMessage(input) : null;

        if (input.personCount() == null) {
            return new Decision(null, 0, null,
                    "Waiting for the first person count from the vision service.",
                    "No detection data yet", alert, alertMessage);
        }

        int base = baseSetpoint(input.personCount());

        if (input.temperature() == null) {
            // Occupancy-only fallback. Reported as such rather than silently passed
            // off as a full recommendation (Section 20.5).
            return new Decision(base, 0, clamp(base),
                    String.format(Locale.ROOT,
                            "Set %d °C. No temperature reading - occupancy-only estimate.", clamp(base)),
                    "Occupancy only - no temperature reading", alert, alertMessage);
        }

        double t = input.temperature();
        int adjust = temperatureAdjustment(t);
        int recommended = clamp(base + adjust);

        // The message deliberately does not quote the measured temperature.
        //
        // A published recommendation survives the hysteresis window (Step 4) while the
        // temperature tile beside it keeps updating every few seconds, so a message
        // reading "the room is at 27.2 °C" would sit next to a tile saying 26.4 °C and
        // read as a contradiction. The setpoint and the direction are what the operator
        // acts on; the numbers the decision was taken on live in the recommendations
        // table and in the Step 1/Step 2 breakdown the dashboard shows underneath.
        String message;
        String reason;
        if (adjust > 0) {
            message = String.format(Locale.ROOT,
                    "Raise the AC to %d °C - the room is below the comfort band.", recommended);
            reason = "Raised - room below comfort band";
        } else if (adjust < 0) {
            message = String.format(Locale.ROOT,
                    "Lower the AC to %d °C - the room is above the comfort band.", recommended);
            reason = "Lowered - room above comfort band";
        } else {
            message = String.format(Locale.ROOT,
                    "Maintain %d °C for optimal comfort.", recommended);
            reason = "Optimal for current occupancy";
        }

        return new Decision(base, adjust, recommended, message, reason, alert, alertMessage);
    }

    /**
     * Step 3 - the cold-room alert of Section 9.
     *
     * <p>All three conditions are required: the AC is running, it has been running
     * longer than {@code R_max}, and the room is actually cold. Dropping the
     * temperature term would alert on every long AC cycle in mid-summer; dropping the
     * runtime term would alert every time someone opened a window.
     */
    public boolean isColdRoomAlert(DecisionInput input) {
        return input.acState() == AcState.ON
                && input.acRuntime() != null
                && input.acRuntime().compareTo(config.getAcRuntimeLimit()) > 0
                && input.temperature() != null
                && input.temperature() < config.getColdThreshold();
    }

    private String coldRoomAlertMessage(DecisionInput input) {
        return String.format(Locale.ROOT,
                "Room temperature is %.1f °C and the AC has been ON for %s. Please switch OFF the AC.",
                input.temperature(), formatDuration(input.acRuntime()));
    }

    private int clamp(int value) {
        return Math.max(config.getRecommendationMin(),
                Math.min(config.getRecommendationMax(), value));
    }

    /** "1 h 15 min" / "45 min" - for operator-facing sentences, not for the tile's HH:MM:SS. */
    public static String formatDuration(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d h %02d min", hours, minutes);
        }
        return String.format(Locale.ROOT, "%d min", minutes);
    }
}
