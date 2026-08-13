package com.smartroom.backend.service;

import com.smartroom.backend.config.SmartRoomProperties;
import com.smartroom.backend.domain.RoomData;
import com.smartroom.backend.repository.AcStatusRepository;
import com.smartroom.backend.repository.RoomDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Assembles a {@link RoomState} snapshot. */
@Service
public class RoomStateService {

    private final RoomDataRepository roomDataRepository;
    private final AcStatusRepository acStatusRepository;
    private final SmartRoomProperties properties;

    public RoomStateService(RoomDataRepository roomDataRepository,
                            AcStatusRepository acStatusRepository,
                            SmartRoomProperties properties) {
        this.roomDataRepository = roomDataRepository;
        this.acStatusRepository = acStatusRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RoomState currentState(String roomId) {
        return currentState(roomId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public RoomState currentState(String roomId, LocalDateTime evaluatedAt) {
        Optional<RoomData> temp =
                roomDataRepository.findFirstByRoomIdAndTemperatureIsNotNullOrderByRecordedAtDesc(roomId);
        Optional<RoomData> humidity =
                roomDataRepository.findFirstByRoomIdAndHumidityIsNotNullOrderByRecordedAtDesc(roomId);
        Optional<RoomData> vent =
                roomDataRepository.findFirstByRoomIdAndVentTemperatureIsNotNullOrderByRecordedAtDesc(roomId);
        Optional<RoomData> detection =
                roomDataRepository.findFirstByRoomIdAndPersonCountIsNotNullOrderByRecordedAtDesc(roomId);

        return new RoomState(
                roomId,
                temp.map(d -> toDouble(d.getTemperature())).orElse(null),
                temp.map(RoomData::getRecordedAt).orElse(null),
                humidity.map(d -> toDouble(d.getHumidity())).orElse(null),
                humidity.map(RoomData::getRecordedAt).orElse(null),
                vent.map(d -> toDouble(d.getVentTemperature())).orElse(null),
                vent.map(RoomData::getRecordedAt).orElse(null),
                detection.map(RoomData::getPersonCount).orElse(null),
                detection.map(RoomData::getRecordedAt).orElse(null),
                acStatusRepository.findFirstByRoomIdAndEndTimeIsNullOrderByStartTimeDesc(roomId).orElse(null),
                evaluatedAt
        );
    }

    /**
     * Rooms the scheduled monitor should visit: every room that has ever reported,
     * plus the configured default so a freshly installed system still evaluates and
     * still shows a dashboard before the first sample arrives.
     */
    @Transactional(readOnly = true)
    public List<String> knownRoomIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String defaultRoom = properties.getDashboard().getDefaultRoomId();
        if (defaultRoom != null && !defaultRoom.isBlank()) {
            ids.add(defaultRoom);
        }
        ids.addAll(roomDataRepository.findDistinctRoomIds());
        return new ArrayList<>(ids);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
