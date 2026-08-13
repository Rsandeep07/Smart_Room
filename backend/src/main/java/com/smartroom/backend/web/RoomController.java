package com.smartroom.backend.web;

import com.smartroom.backend.service.AcMonitorService;
import com.smartroom.backend.service.AlertService;
import com.smartroom.backend.service.DashboardService;
import com.smartroom.backend.service.EventLogService;
import com.smartroom.backend.web.dto.AcOverrideRequest;
import com.smartroom.backend.web.dto.AlertResponse;
import com.smartroom.backend.web.dto.EventLogResponse;
import com.smartroom.backend.web.dto.HistoryResponse;
import com.smartroom.backend.web.dto.RoomStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read endpoints for the dashboard, plus the manual AC override (build plan Step 4.3). */
@RestController
@RequestMapping("/api/room/{roomId}")
@Validated
public class RoomController {

    private final DashboardService dashboardService;
    private final AcMonitorService acMonitorService;
    private final EventLogService eventLogService;
    private final AlertService alertService;

    public RoomController(DashboardService dashboardService,
                          AcMonitorService acMonitorService,
                          EventLogService eventLogService,
                          AlertService alertService) {
        this.dashboardService = dashboardService;
        this.acMonitorService = acMonitorService;
        this.eventLogService = eventLogService;
        this.alertService = alertService;
    }

    /** Everything the dashboard renders, in one consistent snapshot. */
    @GetMapping("/status")
    public RoomStatusResponse status(@PathVariable String roomId) {
        return dashboardService.status(roomId);
    }

    /**
     * Series for the two history charts.
     *
     * <p>Capped at 720 hours (30 days). The cap is not about the data volume - buckets
     * handle that - but about the query: an uncapped {@code hours} lets one request scan
     * the whole table.
     */
    @GetMapping("/history")
    public HistoryResponse history(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "24")
            @Min(value = 1, message = "hours must be at least 1")
            @Max(value = 720, message = "hours must be at most 720") int hours) {
        return dashboardService.history(roomId, hours);
    }

    /** Rows for the Logs panel. */
    @GetMapping("/logs")
    public List<EventLogResponse> logs(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit) {
        return eventLogService.recent(roomId, limit).stream().map(EventLogResponse::from).toList();
    }

    /** Alert history for the Alerts view. */
    @GetMapping("/alerts")
    public List<AlertResponse> alerts(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit) {
        return alertService.recent(roomId, limit).stream().map(AlertResponse::from).toList();
    }

    /**
     * Section 10 Option A - manual override, retained as a fallback for when the vent
     * probe is unplugged or being recalibrated.
     *
     * <p>Returns the fresh status so the dashboard reflects the change on the same
     * round trip instead of waiting for its next poll.
     */
    @PostMapping("/ac")
    public RoomStatusResponse overrideAcStatus(@PathVariable String roomId,
                                               @Valid @RequestBody AcOverrideRequest request) {
        acMonitorService.applyManualOverride(roomId, request.status(), request.note());
        return dashboardService.status(roomId);
    }

    /** Hands AC detection back to the vent probe before the override would expire. */
    @DeleteMapping("/ac")
    public ResponseEntity<RoomStatusResponse> clearAcOverride(@PathVariable String roomId) {
        acMonitorService.clearManualOverride(roomId);
        return ResponseEntity.ok(dashboardService.status(roomId));
    }
}
