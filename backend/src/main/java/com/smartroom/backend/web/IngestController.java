package com.smartroom.backend.web;

import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.service.AlertService;
import com.smartroom.backend.service.PublishedRecommendation;
import com.smartroom.backend.service.RecommendationService;
import com.smartroom.backend.service.TelemetryService;
import com.smartroom.backend.web.dto.DetectionRequest;
import com.smartroom.backend.web.dto.IngestResponse;
import com.smartroom.backend.web.dto.RoomDataRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two ingest endpoints of Section 11, both behind the API-key filter.
 *
 * <p>Each ingest re-evaluates the rules immediately rather than waiting for the
 * 60-second monitor tick. That is what lets the build plan's Step 6 gate hold - the
 * dashboard reflecting an occupancy change within 15 seconds - given the vision
 * service posts every 10 seconds and the dashboard polls every 5.
 */
@RestController
@RequestMapping("/api")
public class IngestController {

    private final TelemetryService telemetryService;
    private final RecommendationService recommendationService;
    private final AlertService alertService;

    public IngestController(TelemetryService telemetryService,
                            RecommendationService recommendationService,
                            AlertService alertService) {
        this.telemetryService = telemetryService;
        this.recommendationService = recommendationService;
        this.alertService = alertService;
    }

    /** Sensor sample from the ESP32-CAM: temperature, humidity, vent temperature. */
    @PostMapping("/room/data")
    public ResponseEntity<IngestResponse> ingestSensorSample(@Valid @RequestBody RoomDataRequest request) {
        if (request.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one of temperature, humidity or ventTemperature must be present");
        }
        RoomData saved = telemetryService.recordSensorSample(request);
        return ResponseEntity.ok(respond(saved));
    }

    /** Person count from the Python vision service. */
    @PostMapping("/detection")
    public ResponseEntity<IngestResponse> ingestDetection(@Valid @RequestBody DetectionRequest request) {
        RoomData saved = telemetryService.recordDetection(request);
        return ResponseEntity.ok(respond(saved));
    }

    private IngestResponse respond(RoomData saved) {
        PublishedRecommendation recommendation =
                recommendationService.evaluateForIngest(saved.getRoomId(), saved.getRecordedAt());
        return new IngestResponse(
                "accepted",
                saved.getId(),
                saved.getRoomId(),
                saved.getRecordedAt(),
                recommendation.recommendedTemperature(),
                alertService.activeAlert(saved.getRoomId()).isPresent());
    }
}
