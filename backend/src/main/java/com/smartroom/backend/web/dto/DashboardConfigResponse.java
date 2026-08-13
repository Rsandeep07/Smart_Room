package com.smartroom.backend.web.dto;

import java.util.List;

/**
 * Body of {@code GET /api/config} - what the dashboard needs before it can render.
 *
 * <p>The camera stream URL lives here rather than in the frontend build because it is
 * the ESP32-CAM's DHCP address, which changes when the board reconnects or moves to
 * another room. A value baked into the React bundle would need a rebuild each time;
 * this one needs a property change and a restart.
 */
public record DashboardConfigResponse(
        String defaultRoomId,
        List<String> rooms,
        String cameraStreamUrl,
        int pollIntervalSeconds
) {
}
