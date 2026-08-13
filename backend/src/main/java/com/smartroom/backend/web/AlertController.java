package com.smartroom.backend.web;

import com.smartroom.backend.service.AlertService;
import com.smartroom.backend.web.dto.AlertResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dismissal of the dashboard alert banner (build plan Step 6.5). */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledge(@PathVariable Long alertId) {
        return alertService.acknowledge(alertId)
                .map(AlertResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
