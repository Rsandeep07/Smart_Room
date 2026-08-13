package com.smartroom.backend.engine;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.AcState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Section 21 rules.
 *
 * <p>These exist so the calibration work of build plan Step 7 has something to hold
 * onto. The engine is the part of the system that has to be defended under
 * examination, and "it looked right on the dashboard" is not a defence.
 */
class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine(new SmartRoomProperties());

    @Nested
    @DisplayName("Step 1 - occupancy table (Section 8)")
    class BaseSetpoint {

        @ParameterizedTest(name = "{0} people -> {1} °C")
        @CsvSource({
                "0, 26", "5, 26",
                "6, 25", "15, 25",
                "16, 24", "25, 24",
                "26, 23", "40, 23",
                "41, 22", "120, 22"
        })
        void mapsOccupancyToBaseSetpoint(int people, int expected) {
            assertThat(engine.baseSetpoint(people)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Step 2 - temperature correction")
    class TemperatureCorrection {

        @ParameterizedTest(name = "{0} °C -> {1} K")
        @CsvSource({
                // Below T_target - 1: raise the setpoint, back off cooling.
                "18.0, 1", "22.9, 1",
                // Dead band. Note it is asymmetric by design: it runs from
                // T_target - 1 up to T_target + 2, so a room one degree warm is left alone.
                "23.0, 0", "24.0, 0", "25.0, 0", "26.0, 0",
                // Above T_target + 2: lower the setpoint, cool harder.
                "26.1, -1", "30.0, -1"
        })
        void correctsFromMeasuredTemperature(double temperature, int expected) {
            assertThat(engine.temperatureAdjustment(temperature)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Section 20.5 - the failure the revised rules exist to fix")
    class OccupancyOnlyRegression {

        @Test
        void doesNotRecommendTwentyTwoDegreesInANineteenDegreeRoom() {
            // 45 people at 19 °C. The Section 8 table alone says 22 °C, which is the
            // opposite of what an already-overcooled room needs.
            Decision decision = engine.evaluate(
                    new DecisionInput(45, 19.0, AcState.OFF, Duration.ZERO));

            assertThat(decision.baseTemperature()).isEqualTo(22);
            assertThat(decision.adjustment()).isEqualTo(1);
            assertThat(decision.recommendedTemperature()).isEqualTo(23);
        }

        @Test
        void lowersSetpointForACrowdedWarmRoom() {
            Decision decision = engine.evaluate(
                    new DecisionInput(45, 29.0, AcState.ON, Duration.ofMinutes(5)));

            assertThat(decision.baseTemperature()).isEqualTo(22);
            assertThat(decision.adjustment()).isEqualTo(-1);
            // Clamped at recommendation-min rather than dropping to 21.
            assertThat(decision.recommendedTemperature()).isEqualTo(22);
        }

        @Test
        void clampsToTheConfiguredUpperBound() {
            Decision decision = engine.evaluate(
                    new DecisionInput(2, 18.0, AcState.OFF, Duration.ZERO));

            assertThat(decision.baseTemperature()).isEqualTo(26);
            assertThat(decision.adjustment()).isEqualTo(1);
            assertThat(decision.recommendedTemperature()).isEqualTo(27);
        }
    }

    @Nested
    @DisplayName("Step 3 - cold-room alert (Section 9)")
    class ColdRoomAlert {

        @Test
        void firesWhenAcIsOnTooLongAndTheRoomIsCold() {
            Decision decision = engine.evaluate(
                    new DecisionInput(25, 19.0, AcState.ON, Duration.ofMinutes(75)));

            assertThat(decision.alert()).isTrue();
            assertThat(decision.alertMessage())
                    .contains("19.0 °C")
                    .contains("1 h 15 min")
                    .contains("Please switch OFF the AC");
        }

        @Test
        void doesNotFireWhenTheAcIsOff() {
            assertThat(engine.evaluate(
                    new DecisionInput(25, 19.0, AcState.OFF, Duration.ofMinutes(75))).alert())
                    .isFalse();
        }

        @Test
        void doesNotFireBeforeTheRuntimeLimit() {
            assertThat(engine.evaluate(
                    new DecisionInput(25, 19.0, AcState.ON, Duration.ofMinutes(45))).alert())
                    .isFalse();
        }

        @Test
        void doesNotFireWhenTheRoomIsNotActuallyCold() {
            // A long AC cycle in mid-summer is not a fault; without the temperature
            // term this would alert every afternoon and be ignored by the second day.
            assertThat(engine.evaluate(
                    new DecisionInput(25, 24.0, AcState.ON, Duration.ofHours(3))).alert())
                    .isFalse();
        }

        @Test
        void doesNotFireWithoutATemperatureReading() {
            assertThat(engine.evaluate(
                    new DecisionInput(25, null, AcState.ON, Duration.ofHours(3))).alert())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Section 16 - the worked example")
    class WorkedExample {

        @Test
        void reproducesTheDocumentedScenario() {
            // 25 people, 19 °C, AC ON for 1 h 15 min.
            Decision decision = engine.evaluate(
                    new DecisionInput(25, 19.0, AcState.ON, Duration.ofMinutes(75)));

            assertThat(decision.baseTemperature()).isEqualTo(24);
            assertThat(decision.adjustment()).isEqualTo(1);
            assertThat(decision.recommendedTemperature()).isEqualTo(25);
            assertThat(decision.alert()).isTrue();
        }
    }

    @Nested
    @DisplayName("Missing telemetry")
    class MissingTelemetry {

        @Test
        void reportsWaitingBeforeTheFirstPersonCount() {
            Decision decision = engine.evaluate(
                    new DecisionInput(null, 22.0, AcState.OFF, Duration.ZERO));

            assertThat(decision.recommendedTemperature()).isNull();
            assertThat(decision.reason()).isEqualTo("No detection data yet");
        }

        @Test
        void labelsAnOccupancyOnlyEstimateAsSuch() {
            Decision decision = engine.evaluate(
                    new DecisionInput(20, null, AcState.OFF, Duration.ZERO));

            assertThat(decision.recommendedTemperature()).isEqualTo(24);
            assertThat(decision.adjustment()).isZero();
            assertThat(decision.reason()).isEqualTo("Occupancy only - no temperature reading");
            assertThat(decision.message()).contains("occupancy-only estimate");
        }
    }

    @Nested
    @DisplayName("Configurability")
    class Configurability {

        @Test
        void honoursARecalibratedTargetTemperature() {
            // Step 7 requires these to be tuned per room; a recompile to change a
            // threshold means the threshold does not get tuned.
            SmartRoomProperties properties = new SmartRoomProperties();
            properties.getEngine().setTargetTemperature(26.0);
            DecisionEngine warmer = new DecisionEngine(properties);

            assertThat(warmer.temperatureAdjustment(24.0)).isEqualTo(1);
            assertThat(engine.temperatureAdjustment(24.0)).isZero();
        }

        @Test
        void honoursARecalibratedColdThreshold() {
            SmartRoomProperties properties = new SmartRoomProperties();
            properties.getEngine().setColdThreshold(23.0);
            DecisionEngine fussier = new DecisionEngine(properties);

            DecisionInput input = new DecisionInput(25, 22.0, AcState.ON, Duration.ofMinutes(75));
            assertThat(fussier.evaluate(input).alert()).isTrue();
            assertThat(engine.evaluate(input).alert()).isFalse();
        }
    }
}
