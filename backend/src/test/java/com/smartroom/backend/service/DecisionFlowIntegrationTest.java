package com.smartroom.backend.service;

import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.AcStatus;
import com.smartroom.backend.domain.AcStatusSource;
import com.smartroom.backend.domain.Alert;
import com.smartroom.backend.domain.Recommendation;
import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.repository.AcStatusRepository;
import com.smartroom.backend.repository.AlertRepository;
import com.smartroom.backend.repository.RecommendationRepository;
import com.smartroom.backend.repository.RoomDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the two behaviours that cannot be tested through {@link
 * com.smartroom.backend.engine.DecisionEngine} alone, because both are about state
 * over time rather than about the rules: the alert lifecycle and Step 4 hysteresis.
 *
 * <p>The scheduled monitor is pushed an hour out. Leaving it enabled would let a tick
 * fire mid-test, derive AC state from this test's own fixture rows and open intervals
 * underneath the assertions.
 */
@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "smartroom.ac.monitor-initial-delay-ms=3600000",
        "smartroom.ac.monitor-interval-ms=3600000"
})
class DecisionFlowIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private RoomDataRepository roomDataRepository;
    @Autowired
    private AcStatusRepository acStatusRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private RecommendationRepository recommendationRepository;

    private String roomId;

    /** A distinct room per test: the H2 instance is shared across the class. */
    @BeforeEach
    void setUp() {
        roomId = "T-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Cold-room alert is raised once per AC cycle, not once per evaluation")
    void raisesColdRoomAlertOncePerCycle() {
        givenSensorSample(19.0, 50.0, 12.0);
        givenPersonCount(25);
        givenAcRunningFor(Duration.ofMinutes(70));

        recommendationService.evaluate(roomId);
        recommendationService.evaluate(roomId);
        recommendationService.evaluate(roomId);

        List<Alert> raised = alertRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10));
        assertThat(raised).hasSize(1);
        assertThat(raised.get(0).getMessage())
                .contains("19.0 °C")
                .contains("Please switch OFF the AC");
        assertThat(raised.get(0).getAcknowledgedAt()).isNull();
    }

    @Test
    @DisplayName("Alert clears itself once the AC is switched off")
    void resolvesAlertWhenAcGoesOff() {
        givenSensorSample(19.0, 50.0, 12.0);
        givenPersonCount(25);
        AcStatus running = givenAcRunningFor(Duration.ofMinutes(70));
        recommendationService.evaluate(roomId);
        assertThat(activeAlert()).isPresent();

        // The receptionist acts on the alert: AC off, vent warms back to room temperature.
        running.setEndTime(LocalDateTime.now());
        acStatusRepository.save(running);
        acStatusRepository.save(AcStatus.builder()
                .roomId(roomId).status(AcState.OFF).source(AcStatusSource.VENT_PROBE)
                .startTime(LocalDateTime.now()).build());

        recommendationService.evaluate(roomId);

        assertThat(activeAlert()).isEmpty();
        assertThat(alertRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(alert -> assertThat(alert.getAcknowledgedAt()).isNotNull());
    }

    @Test
    @DisplayName("Alert does not fire before the runtime limit, however cold the room is")
    void doesNotAlertBeforeRuntimeLimit() {
        givenSensorSample(17.0, 50.0, 10.0);
        givenPersonCount(25);
        givenAcRunningFor(Duration.ofMinutes(20));

        recommendationService.evaluate(roomId);

        assertThat(activeAlert()).isEmpty();
    }

    @Test
    @DisplayName("Step 4 - a changed setpoint is held back, reported as pending, then published")
    void appliesHysteresisToSetpointChanges() {
        givenSensorSample(24.0, 45.0, 23.5);
        givenPersonCount(20);

        PublishedRecommendation first = recommendationService.evaluate(roomId);
        assertThat(first.recommendedTemperature()).isEqualTo(24);
        assertThat(first.pendingTemperature()).isNull();

        // The room fills up. Occupancy alone would move the setpoint to 22 immediately.
        givenPersonCount(45);
        PublishedRecommendation held = recommendationService.evaluate(roomId);

        assertThat(held.recommendedTemperature())
                .as("published value must not move inside the hysteresis window")
                .isEqualTo(24);
        assertThat(held.pendingTemperature()).isEqualTo(22);
        assertThat(held.holdUntil()).isNotNull();
        assertThat(recommendationRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .as("no second row while the change is held back")
                .hasSize(1);

        // Once the window has elapsed the change is published.
        backdateLatestRecommendationBy(Duration.ofMinutes(11));
        PublishedRecommendation published = recommendationService.evaluate(roomId);

        assertThat(published.recommendedTemperature()).isEqualTo(22);
        assertThat(published.pendingTemperature()).isNull();
        assertThat(recommendationRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .hasSize(2);
    }

    @Test
    @DisplayName("An unchanged decision never writes a second row")
    void doesNotRewriteAnUnchangedDecision() {
        givenSensorSample(24.0, 45.0, 23.5);
        givenPersonCount(20);

        recommendationService.evaluate(roomId);
        backdateLatestRecommendationBy(Duration.ofMinutes(30));
        recommendationService.evaluate(roomId);
        recommendationService.evaluate(roomId);

        assertThat(recommendationRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .as("recommendations is an audit trail of changes, not a poll log")
                .hasSize(1);
    }

    @Test
    @DisplayName("An occupancy-only start is replaced once a temperature arrives, even at the same setpoint")
    void replacesOccupancyOnlyWordingWhenTemperatureArrives() {
        // No temperature yet: 20 people gives 24 °C from the table alone.
        givenPersonCount(20);
        PublishedRecommendation occupancyOnly = recommendationService.evaluate(roomId);
        assertThat(occupancyOnly.recommendedTemperature()).isEqualTo(24);
        assertThat(occupancyOnly.message()).contains("occupancy-only estimate");

        // A temperature inside the dead band lands on the same 24 °C. The number has not
        // changed, but the explanation has, and a stale explanation beside a correct
        // value is what makes an operator distrust the dashboard.
        givenSensorSample(24.0, 45.0, 23.5);
        backdateLatestRecommendationBy(Duration.ofMinutes(11));
        PublishedRecommendation full = recommendationService.evaluate(roomId);

        assertThat(full.recommendedTemperature()).isEqualTo(24);
        assertThat(full.message()).doesNotContain("occupancy-only estimate");
        assertThat(full.reason()).isEqualTo("Optimal for current occupancy");
    }

    @Test
    @DisplayName("A back-dated sample does not start the hysteresis clock")
    void backDatedIngestDoesNotPublish() {
        // A replayed reading from the ESP32's offline buffer, describing the room half an
        // hour ago. If this published, it would pin the setpoint for the whole hysteresis
        // window and the dashboard would show a value derived from stale data.
        roomDataRepository.save(RoomData.builder()
                .roomId(roomId)
                .temperature(BigDecimal.valueOf(30.0))
                .humidity(BigDecimal.valueOf(60.0))
                .ventTemperature(BigDecimal.valueOf(29.5))
                .recordedAt(LocalDateTime.now().minusMinutes(30))
                .build());
        roomDataRepository.save(RoomData.builder()
                .roomId(roomId)
                .personCount(2)
                .recordedAt(LocalDateTime.now().minusMinutes(30))
                .build());

        PublishedRecommendation skipped =
                recommendationService.evaluateForIngest(roomId, LocalDateTime.now().minusMinutes(30));

        assertThat(skipped.recommendedTemperature()).isNull();
        assertThat(recommendationRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .isEmpty();

        // A live sample publishes straight away, against current state.
        givenSensorSample(24.0, 45.0, 23.5);
        givenPersonCount(30);
        PublishedRecommendation live = recommendationService.evaluateForIngest(roomId, LocalDateTime.now());

        assertThat(live.recommendedTemperature()).isEqualTo(23);
        assertThat(recommendationRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 10)))
                .hasSize(1);
    }

    // --- fixtures ---------------------------------------------------------------

    private void givenSensorSample(double temperature, double humidity, double ventTemperature) {
        roomDataRepository.save(RoomData.builder()
                .roomId(roomId)
                .temperature(BigDecimal.valueOf(temperature))
                .humidity(BigDecimal.valueOf(humidity))
                .ventTemperature(BigDecimal.valueOf(ventTemperature))
                .recordedAt(LocalDateTime.now())
                .build());
    }

    private void givenPersonCount(int personCount) {
        roomDataRepository.save(RoomData.builder()
                .roomId(roomId)
                .personCount(personCount)
                .recordedAt(LocalDateTime.now())
                .build());
    }

    private AcStatus givenAcRunningFor(Duration elapsed) {
        return acStatusRepository.save(AcStatus.builder()
                .roomId(roomId)
                .status(AcState.ON)
                .source(AcStatusSource.VENT_PROBE)
                .startTime(LocalDateTime.now().minus(elapsed))
                .build());
    }

    /** Moves the published recommendation into the past so the hysteresis window has elapsed. */
    private void backdateLatestRecommendationBy(Duration amount) {
        Recommendation latest = recommendationRepository.findFirstByRoomIdOrderByCreatedAtDesc(roomId).orElseThrow();
        latest.setCreatedAt(latest.getCreatedAt().minus(amount));
        recommendationRepository.save(latest);
    }

    private java.util.Optional<Alert> activeAlert() {
        return alertRepository.findFirstByRoomIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(roomId);
    }
}
