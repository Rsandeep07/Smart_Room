package com.smartroom.backend.service;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.AcState;
import com.smartroom.backend.domain.AcStatus;
import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.repository.RoomDataRepository;
import com.smartroom.backend.web.dto.AlertResponse;
import com.smartroom.backend.web.dto.HistoryResponse;
import com.smartroom.backend.web.dto.RoomStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Assembles the read models the React dashboard consumes.
 *
 * <p>Read-only: the ingest endpoints and the monitor own all state changes. This
 * class only joins what they have already recorded.
 */
@Service
public class DashboardService {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_ALERT = "ALERT";
    public static final String STATUS_DEGRADED = "DEGRADED";

    private final RoomDataRepository roomDataRepository;
    private final RoomStateService roomStateService;
    private final RecommendationService recommendationService;
    private final AlertService alertService;
    private final AcMonitorService acMonitorService;
    private final SmartRoomProperties properties;

    public DashboardService(RoomDataRepository roomDataRepository,
                            RoomStateService roomStateService,
                            RecommendationService recommendationService,
                            AlertService alertService,
                            AcMonitorService acMonitorService,
                            SmartRoomProperties properties) {
        this.roomDataRepository = roomDataRepository;
        this.roomStateService = roomStateService;
        this.recommendationService = recommendationService;
        this.alertService = alertService;
        this.acMonitorService = acMonitorService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RoomStatusResponse status(String roomId) {
        RoomState state = roomStateService.currentState(roomId);
        PublishedRecommendation recommendation = recommendationService.current(roomId);
        AlertResponse alert = alertService.activeAlert(roomId).map(AlertResponse::from).orElse(null);

        Duration staleTimeout = properties.getAc().getStaleReadingTimeout();
        boolean sensorOnline = state.isSensorFresh(staleTimeout);
        boolean visionOnline = state.isVisionFresh(staleTimeout);

        AcStatus interval = state.acInterval();
        Duration acDuration = state.acRuntime();

        return new RoomStatusResponse(
                roomId,
                state.evaluatedAt(),
                systemStatus(alert != null, sensorOnline, visionOnline),

                state.temperature(),
                state.temperatureAt(),
                state.humidity(),
                state.humidityAt(),
                state.ventTemperature(),
                state.ventTemperatureAt(),
                round1(state.ventDelta()),
                state.personCount(),
                state.personCountAt(),
                sensorOnline,
                visionOnline,

                recommendation.recommendedTemperature(),
                recommendation.message(),
                recommendation.reason(),
                recommendation.baseTemperature(),
                recommendation.adjustment(),
                recommendation.publishedAt(),
                recommendation.pendingTemperature(),
                recommendation.holdUntil(),

                state.acState(),
                interval == null ? null : interval.getSource(),
                state.acState() == AcState.ON ? "Cooling Mode" : "Idle",
                interval == null ? null : interval.getStartTime(),
                acDuration.getSeconds(),
                formatHhMmSs(acDuration),
                acMonitorService.manualOverrideExpiry(roomId),

                alert,
                thresholds()
        );
    }

    /**
     * DEGRADED is reported when either producer has gone quiet.
     *
     * <p>Without it the dashboard would keep showing the last known temperature
     * against a green "System Normal" pill after the ESP32 had browned out - which is
     * precisely the failure Section 20.1 says to expect.
     */
    private String systemStatus(boolean hasAlert, boolean sensorOnline, boolean visionOnline) {
        if (hasAlert) {
            return STATUS_ALERT;
        }
        if (!sensorOnline || !visionOnline) {
            return STATUS_DEGRADED;
        }
        return STATUS_NORMAL;
    }

    private RoomStatusResponse.Thresholds thresholds() {
        SmartRoomProperties.Engine engine = properties.getEngine();
        return new RoomStatusResponse.Thresholds(
                engine.getTargetTemperature(),
                engine.getColdThreshold(),
                engine.getAcRuntimeLimit().toMinutes(),
                properties.getAc().getVentDeltaThreshold(),
                engine.getHysteresisInterval().toMinutes(),
                engine.getRecommendationMin(),
                engine.getRecommendationMax());
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(String roomId, int hours) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(hours);
        int bucketMinutes = bucketMinutesFor(hours);

        List<RoomData> temperatureRows =
                roomDataRepository.findByRoomIdAndTemperatureIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(roomId, from);
        List<RoomData> humidityRows =
                roomDataRepository.findByRoomIdAndHumidityIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(roomId, from);
        List<RoomData> personRows =
                roomDataRepository.findByRoomIdAndPersonCountIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(roomId, from);

        return new HistoryResponse(
                roomId, hours, from, to, bucketMinutes,
                bucket(temperatureRows, from, to, bucketMinutes, d -> toDouble(d.getTemperature())),
                bucket(humidityRows, from, to, bucketMinutes, d -> toDouble(d.getHumidity())),
                bucket(personRows, from, to, bucketMinutes, d -> d.getPersonCount().doubleValue()));
    }

    /**
     * Bucket width per range, so every range returns roughly 100 to 150 points.
     *
     * <p>A 24-hour view of raw data would be over eleven thousand points across the
     * two series: slow to serialise, slow to render, and no more readable than a
     * hundred.
     */
    private static int bucketMinutesFor(int hours) {
        if (hours <= 1) {
            return 1;
        }
        if (hours <= 6) {
            return 3;
        }
        if (hours <= 24) {
            return 10;
        }
        if (hours <= 24 * 7) {
            return 60;
        }
        return 180;
    }

    /**
     * Averages samples onto a fixed bucket grid.
     *
     * <p>Every bucket in the range is emitted, with a null value where no sample
     * landed. That is what lets the chart draw a break in the line for a Wi-Fi outage
     * instead of interpolating a straight line across it and implying data that was
     * never measured.
     */
    private static List<HistoryResponse.Point> bucket(List<RoomData> rows,
                                                      LocalDateTime from,
                                                      LocalDateTime to,
                                                      int bucketMinutes,
                                                      Function<RoomData, Double> extractor) {
        Map<Long, double[]> sums = new LinkedHashMap<>();
        long bucketSeconds = bucketMinutes * 60L;

        for (RoomData row : rows) {
            Double value = extractor.apply(row);
            if (value == null) {
                continue;
            }
            long index = ChronoUnit.SECONDS.between(from, row.getRecordedAt()) / bucketSeconds;
            double[] acc = sums.computeIfAbsent(index, k -> new double[2]);
            acc[0] += value;
            acc[1] += 1;
        }

        long bucketCount = Math.max(1, ChronoUnit.SECONDS.between(from, to) / bucketSeconds);
        List<HistoryResponse.Point> points = new ArrayList<>((int) bucketCount + 1);
        for (long i = 0; i <= bucketCount; i++) {
            LocalDateTime t = from.plusSeconds(i * bucketSeconds);
            if (t.isAfter(to)) {
                break;
            }
            double[] acc = sums.get(i);
            Double value = acc == null ? null : round1(acc[0] / acc[1]);
            points.add(new HistoryResponse.Point(t, value));
        }
        return points;
    }

    /** HH:MM:SS, as the AC Running Duration tile displays it. */
    public static String formatHhMmSs(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static Double round1(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
