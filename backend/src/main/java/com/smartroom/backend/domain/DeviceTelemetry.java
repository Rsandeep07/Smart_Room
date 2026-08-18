package com.smartroom.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_telemetry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "humidity", precision = 5, scale = 2)
    private BigDecimal humidity;

    @Column(name = "device_status", length = 32)
    private String deviceStatus;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}