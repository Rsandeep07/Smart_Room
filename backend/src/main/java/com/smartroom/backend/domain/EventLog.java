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

import java.time.LocalDateTime;

/**
 * An operational event (AC turned ON, recommendation changed, alert raised).
 *
 * <p>Not in Section 13. This backs the dashboard's Logs panel (Section 14) and
 * gives the structured logging of build plan Step 8.3 somewhere queryable to live.
 * Events are written on state transitions only, so the table stays readable - it is
 * a narrative of what the system did, not a copy of the telemetry.
 */
@Entity
@Table(name = "event_log", indexes = @Index(name = "idx_event_log_room_created", columnList = "room_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", length = 32)
    private String roomId;

    /** INFO or WARNING. Drives the dot colour in the Logs panel. */
    @Column(name = "level", nullable = false, length = 16)
    private String level;

    /** Machine-readable discriminator, e.g. {@code AC_STATE_CHANGE}. */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
