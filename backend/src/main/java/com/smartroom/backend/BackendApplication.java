package com.smartroom.backend;

import com.smartroom.backend.config.SmartRoomProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Classroom Monitoring and AC Recommendation System - backend.
 *
 * <p>The central decision layer of Section 3: it ingests telemetry from the
 * ESP32-CAM and person counts from the Python vision service, derives AC state from
 * the supply-vent probe, applies the Section 21 rules, and serves the React
 * dashboard. It does not control the AC (Section 18).
 */
@SpringBootApplication
@EnableConfigurationProperties(SmartRoomProperties.class)
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
