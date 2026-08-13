package com.smartroom.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A raised alert (Section 9), as a dismissable record.
 *
 * <p>Not in Section 13. The dashboard in Section 14 needs a banner the receptionist
 * can dismiss and that carries a timestamp, which the {@code recommendations.alert}
 * boolean cannot express. {@link #acStatusId} scopes an alert to the AC cycle that
 * caused it, so a room that stays cold for two hours raises one alert rather than
 * one per minute of the monitor's schedule.
 */
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_room_created", columnList = "room_id, created_at"),
        @Index(name = "idx_alerts_room_ack", columnList = "room_id, acknowledged_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    /** Machine-readable discriminator, e.g. {@code COLD_ROOM_AC_RUNTIME}. */
    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;

    /** WARNING or CRITICAL. Drives the banner treatment, always alongside an icon and label. */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    /** Room temperature at the moment the alert was raised. */
    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    /** AC runtime at the moment the alert was raised. */
    @Column(name = "ac_runtime_seconds")
    private Long acRuntimeSeconds;

    /** The {@link AcStatus} interval this alert belongs to; null for alerts not tied to a cycle. */
    @Column(name = "ac_status_id")
    private Long acStatusId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Set when the receptionist dismisses the banner. Null while the alert is active. */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
}
