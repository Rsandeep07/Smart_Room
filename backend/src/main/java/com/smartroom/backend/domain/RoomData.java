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
 * One telemetry sample (Section 13, {@code room_data}).
 *
 * <p>A sample is deliberately sparse. The ESP32-CAM posts temperature, humidity
 * and vent temperature; the Python vision service posts a person count. Neither
 * knows the other's values, so no single row is complete and the nullable columns
 * are the normal case rather than an error. Current room state is assembled from
 * the most recent non-null value of each measurement.
 */
@Entity
@Table(name = "room_data", indexes = @Index(name = "idx_room_data_room_time", columnList = "room_id, recorded_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    /** Room air temperature in degrees Celsius, from the DHT22. */
    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    /** Relative humidity in percent, from the DHT22. */
    @Column(name = "humidity", precision = 5, scale = 2)
    private BigDecimal humidity;

    /** AC supply-vent temperature in degrees Celsius, from the DS18B20 probe (Section 20.3). */
    @Column(name = "vent_temperature", precision = 5, scale = 2)
    private BigDecimal ventTemperature;

    /** Median person count over the reporting window, from the vision service (Section 20.4). */
    @Column(name = "person_count")
    private Integer personCount;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
