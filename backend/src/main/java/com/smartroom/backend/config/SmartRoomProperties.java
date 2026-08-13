package com.smartroom.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Every tunable of the decision engine and the AC monitor, bound from
 * {@code smartroom.*}.
 *
 * <p>Nothing here is hard-coded in the services on purpose. Build plan Step 7
 * requires the vent delta and the comfort thresholds to be recalibrated against
 * measurements taken in the real classroom, and a threshold you have to recompile
 * to change does not get recalibrated.
 */
@ConfigurationProperties(prefix = "smartroom")
@Getter
@Setter
public class SmartRoomProperties {

    private Engine engine = new Engine();
    private Ac ac = new Ac();
    private Security security = new Security();
    private Dashboard dashboard = new Dashboard();

    /** Section 21 configuration block. */
    @Getter
    @Setter
    public static class Engine {
        /** T_target - the comfort setpoint the temperature correction is measured against. */
        private double targetTemperature = 24.0;
        /** T_cold - below this the room counts as too cold for the alert rule. */
        private double coldThreshold = 21.0;
        /** R_max - AC runtime beyond which the cold-room alert can fire. */
        private Duration acRuntimeLimit = Duration.ofMinutes(60);
        /** Lower clamp on the recommended setpoint. */
        private int recommendationMin = 22;
        /** Upper clamp on the recommended setpoint. */
        private int recommendationMax = 27;
        /** Step 4 - minimum interval between two different published recommendations. */
        private Duration hysteresisInterval = Duration.ofMinutes(10);
    }

    /** Section 20.3 Option C configuration block. */
    @Getter
    @Setter
    public static class Ac {
        /** Room minus vent temperature above which the AC is judged to be running. */
        private double ventDeltaThreshold = 4.0;
        /** Telemetry older than this is not trusted to decide AC state. */
        private Duration staleReadingTimeout = Duration.ofMinutes(5);
        /** How long a dashboard override outranks the vent probe. */
        private Duration manualOverrideTtl = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class Security {
        private String apiKey = "smart-room-dev-key";
        private String apiKeyHeader = "X-API-Key";
    }

    @Getter
    @Setter
    public static class Dashboard {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
        private String defaultRoomId = "ROOM101";
        private String cameraStreamUrl = "";
    }
}
