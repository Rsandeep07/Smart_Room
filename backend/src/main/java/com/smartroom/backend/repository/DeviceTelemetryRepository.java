package com.smartroom.backend.repository;

import com.smartroom.backend.domain.DeviceTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, Long> {

    Optional<DeviceTelemetry> findFirstByDeviceIdOrderByRecordedAtDesc(String deviceId);
}