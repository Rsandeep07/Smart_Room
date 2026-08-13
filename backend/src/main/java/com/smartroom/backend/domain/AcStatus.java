package com.smartroom.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One AC ON or OFF interval (Section 13, {@code ac_status}).
 *
 * <p>The current interval is the row whose {@code end_time} is null; there is at
 * most one per room. Section 20.6 flags {@code duration} as a column that drifts
 * out of step with its own timestamps, so it is not stored - {@link #getDuration()}
 * derives it, and an interval that is still open measures up to now.
 */
@Entity
@Table(name = "ac_status", indexes = @Index(name = "idx_ac_status_room_start", columnList = "room_id, start_time"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 8)
    private AcState status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private AcStatusSource source;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** Null while the interval is still running. */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Transient
    public boolean isOpen() {
        return endTime == null;
    }

    /** Elapsed time in this interval, measured to {@code endTime} or to now while open. */
    @Transient
    public Duration getDuration() {
        return Duration.between(startTime, endTime != null ? endTime : LocalDateTime.now());
    }

    /** Elapsed time in this interval as at {@code asOf}, for deterministic rule evaluation. */
    @Transient
    public Duration durationAsOf(LocalDateTime asOf) {
        return Duration.between(startTime, endTime != null ? endTime : asOf);
    }
}
