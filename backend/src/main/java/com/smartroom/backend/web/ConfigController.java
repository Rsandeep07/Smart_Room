package com.smartroom.backend.web;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.service.RoomStateService;
import com.smartroom.backend.web.dto.DashboardConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bootstrap configuration for the dashboard: which room, which camera, how often to poll. */
@RestController
@RequestMapping("/api")
public class ConfigController {

    /** Build plan Step 6.6 - five-second polling. */
    private static final int POLL_INTERVAL_SECONDS = 5;

    private final SmartRoomProperties properties;
    private final RoomStateService roomStateService;

    public ConfigController(SmartRoomProperties properties, RoomStateService roomStateService) {
        this.properties = properties;
        this.roomStateService = roomStateService;
    }

    @GetMapping("/config")
    public DashboardConfigResponse config() {
        return new DashboardConfigResponse(
                properties.getDashboard().getDefaultRoomId(),
                roomStateService.knownRoomIds(),
                properties.getDashboard().getCameraStreamUrl(),
                POLL_INTERVAL_SECONDS);
    }
}
