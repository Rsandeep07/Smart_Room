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
 * A persisted decision of the recommendation engine (Section 13,
 * {@code recommendations}; Section 21 Step 5).
 *
 * <p>Rows are written only when the recommended setpoint or the alert flag
 * actually changes, subject to the Step 4 hysteresis interval. The table is an
 * audit trail of decisions, not a log of every evaluation.
 */
@Entity
@Table(name = "recommendations",
        indexes = @Index(name = "idx_recommendations_room_created", columnList = "room_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    /** Occupancy the decision was taken on. */
    @Column(name = "person_count")
    private Integer personCount;

    /** Room temperature the decision was taken on. */
    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    /** Recommended AC setpoint in degrees Celsius, clamped to the configured band. */
    @Column(name = "recommended_temperature")
    private Integer recommendedTemperature;

    @Column(name = "message", length = 255)
    private String message;

    /** True when this evaluation also satisfied the cold-room alert condition. */
    @Column(name = "alert")
    private Boolean alert;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
