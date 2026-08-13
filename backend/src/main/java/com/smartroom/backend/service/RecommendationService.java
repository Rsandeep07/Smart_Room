package com.smartroom.backend.service;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.Recommendation;
import com.smartroom.backend.engine.Decision;
import com.smartroom.backend.engine.DecisionEngine;
import com.smartroom.backend.repository.RecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Section 21 Steps 4 and 5: hysteresis and persistence around the pure
 * {@link DecisionEngine}.
 *
 * <p>The engine is re-evaluated on every ingest and on every monitor tick, so the
 * alert condition is never more than a few seconds stale. What hysteresis gates is
 * only the <em>published setpoint</em>: a new {@code recommendations} row is written,
 * and the dashboard's number changes, at most once per
 * {@code smartroom.engine.hysteresis-interval}. Alerts are deliberately outside that
 * gate - an overcooled room should not wait ten minutes to say so.
 */
@Service
public class RecommendationService {

    private final DecisionEngine engine;
    private final RecommendationRepository repository;
    private final RoomStateService roomStateService;
    private final AlertService alertService;
    private final EventLogService eventLog;
    private final SmartRoomProperties properties;

    public RecommendationService(DecisionEngine engine,
                                 RecommendationRepository repository,
                                 RoomStateService roomStateService,
                                 AlertService alertService,
                                 EventLogService eventLog,
                                 SmartRoomProperties properties) {
        this.engine = engine;
        this.repository = repository;
        this.roomStateService = roomStateService;
        this.alertService = alertService;
        this.eventLog = eventLog;
        this.properties = properties;
    }

    /** Convenience overload that takes its own snapshot. */
    @Transactional
    public PublishedRecommendation evaluate(String roomId) {
        return evaluate(roomStateService.currentState(roomId));
    }

    /**
     * Evaluation triggered by an ingest, which only happens if the sample describes the
     * room <em>now</em>.
     *
     * <p>A back-dated sample must not drive the decision. Two things produce them: the
     * ESP32's offline buffer replaying readings after a Wi-Fi reconnect (Section 20.6),
     * and any bulk load of historical data. In both cases the samples belong in the
     * history charts, but letting them evaluate has two bad consequences - a reading that
     * describes the room twenty minutes ago becomes the basis of the recommendation shown
     * now, and, worse, the first replayed sample starts the Step 4 hysteresis clock, which
     * then pins the published setpoint to that stale value for the next ten minutes.
     *
     * <p>Skipping evaluation costs nothing: the 60-second monitor tick and the next live
     * sample both re-evaluate against current state.
     *
     * @param recordedAt the sample's own timestamp
     */
    @Transactional
    public PublishedRecommendation evaluateForIngest(String roomId, LocalDateTime recordedAt) {
        LocalDateTime freshFrom = LocalDateTime.now().minus(properties.getAc().getStaleReadingTimeout());
        if (recordedAt != null && recordedAt.isBefore(freshFrom)) {
            return current(roomId);
        }
        return evaluate(roomId);
    }

    /**
     * Evaluates the rules for {@code state}, handles the alert, and publishes the
     * recommendation if hysteresis allows.
     *
     * @return what the dashboard should display
     */
    @Transactional
    public PublishedRecommendation evaluate(RoomState state) {
        Decision decision = engine.evaluate(state.toDecisionInput());
        handleAlert(state, decision);
        return publishIfDue(state, decision);
    }

    /**
     * Raises or clears the cold-room alert.
     *
     * <p>Not subject to hysteresis, and scoped to the AC interval that caused it so a
     * sustained condition produces one banner rather than one per monitor tick.
     */
    private void handleAlert(RoomState state, Decision decision) {
        if (decision.alert()) {
            alertService.raiseColdRoomAlert(
                    state.roomId(),
                    decision.alertMessage(),
                    state.temperature(),
                    state.acRuntime(),
                    state.acInterval() == null ? null : state.acInterval().getId());
        } else {
            alertService.resolveActive(state.roomId(), AlertService.COLD_ROOM_AC_RUNTIME,
                    describeWhyAlertCleared(state));
        }
    }

    private String describeWhyAlertCleared(RoomState state) {
        if (state.acState() == AcState.OFF) {
            return "AC is now OFF";
        }
        if (state.temperature() != null
                && state.temperature() >= properties.getEngine().getColdThreshold()) {
            return "room recovered to %.1f °C".formatted(state.temperature());
        }
        return "condition no longer met";
    }

    /**
     * Step 4 - publish only if this is the first recommendation, or if the decision
     * differs and the hysteresis interval has elapsed since the last publication.
     *
     * <p>The comparison covers the message as well as the setpoint. Without that, the
     * first temperature reading after an occupancy-only start would never replace the
     * "no temperature reading" wording whenever it happened to land on the same
     * number - the dashboard would show a stale explanation beside a correct value.
     */
    private PublishedRecommendation publishIfDue(RoomState state, Decision decision) {
        Optional<Recommendation> lastOpt = repository.findFirstByRoomIdOrderByCreatedAtDesc(state.roomId());

        if (decision.recommendedTemperature() == null) {
            // Nothing to publish yet (no person count). Keep whatever is on record.
            return lastOpt.map(last -> toPublished(last, null, null))
                    .orElseGet(() -> new PublishedRecommendation(
                            null, decision.message(), decision.reason(), null, 0,
                            state.evaluatedAt(), null, null));
        }

        if (lastOpt.isEmpty()) {
            return toPublished(persist(state, decision), null, null);
        }

        Recommendation last = lastOpt.get();
        boolean unchanged = Objects.equals(last.getRecommendedTemperature(), decision.recommendedTemperature())
                && Objects.equals(last.getMessage(), decision.message());
        if (unchanged) {
            return toPublished(last, null, null);
        }

        Duration interval = properties.getEngine().getHysteresisInterval();
        LocalDateTime publishableAt = last.getCreatedAt().plus(interval);
        if (state.evaluatedAt().isBefore(publishableAt)) {
            // Held back. Report the published value plus what is waiting, so the
            // dashboard can explain the lag instead of looking frozen.
            return toPublished(last, decision.recommendedTemperature(), publishableAt);
        }

        Integer previousTemperature = last.getRecommendedTemperature();
        Recommendation saved = persist(state, decision);
        if (!Objects.equals(previousTemperature, decision.recommendedTemperature())) {
            eventLog.info(state.roomId(), EventLogService.RECOMMENDATION_CHANGE,
                    "Recommended AC temp change: %s °C -> %d °C"
                            .formatted(previousTemperature == null ? "--" : previousTemperature,
                                    decision.recommendedTemperature()));
        }
        return toPublished(saved, null, null);
    }

    /** Step 5 - persist the decision. */
    private Recommendation persist(RoomState state, Decision decision) {
        return repository.save(Recommendation.builder()
                .roomId(state.roomId())
                .personCount(state.personCount())
                .temperature(state.temperature() == null ? null
                        : BigDecimal.valueOf(state.temperature()).setScale(2, RoundingMode.HALF_UP))
                .recommendedTemperature(decision.recommendedTemperature())
                .message(decision.message())
                .alert(decision.alert())
                .createdAt(state.evaluatedAt())
                .build());
    }

    /**
     * Rebuilds the display view of a stored recommendation.
     *
     * <p>{@code reason} and the Step 1/Step 2 breakdown are not columns in
     * {@code recommendations} - Section 13 has no room for them - so they are
     * recomputed from the stored inputs. That also means a change to
     * {@code target-temperature} is reflected in the explanation immediately, while
     * the setpoint itself still waits for hysteresis.
     */
    private PublishedRecommendation toPublished(Recommendation row, Integer pending, LocalDateTime holdUntil) {
        Integer base = row.getPersonCount() == null ? null : engine.baseSetpoint(row.getPersonCount());
        int adjustment = row.getTemperature() == null ? 0
                : engine.temperatureAdjustment(row.getTemperature().doubleValue());
        String reason = reasonFor(row, adjustment);
        return new PublishedRecommendation(
                row.getRecommendedTemperature(), row.getMessage(), reason,
                base, adjustment, row.getCreatedAt(), pending, holdUntil);
    }

    private String reasonFor(Recommendation row, int adjustment) {
        if (row.getTemperature() == null) {
            return "Occupancy only - no temperature reading";
        }
        if (adjustment > 0) {
            return "Raised - room below comfort band";
        }
        if (adjustment < 0) {
            return "Lowered - room above comfort band";
        }
        return "Optimal for current occupancy";
    }

    @Transactional(readOnly = true)
    public PublishedRecommendation current(String roomId) {
        RoomState state = roomStateService.currentState(roomId);
        Decision live = engine.evaluate(state.toDecisionInput());
        Optional<Recommendation> lastOpt = repository.findFirstByRoomIdOrderByCreatedAtDesc(roomId);
        if (lastOpt.isEmpty()) {
            return new PublishedRecommendation(null, live.message(), live.reason(), null, 0,
                    state.evaluatedAt(), null, null);
        }
        Recommendation last = lastOpt.get();
        boolean pending = live.recommendedTemperature() != null
                && !Objects.equals(last.getRecommendedTemperature(), live.recommendedTemperature());
        LocalDateTime publishableAt = last.getCreatedAt().plus(properties.getEngine().getHysteresisInterval());
        return pending
                ? toPublished(last, live.recommendedTemperature(), publishableAt)
                : toPublished(last, null, null);
    }
}
