package com.smartroom.backend.repository;

import com.smartroom.backend.domain.RoomData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomDataRepository extends JpaRepository<RoomData, Long> {

    /**
     * Latest sample that actually carries a temperature.
     *
     * <p>The {@code IsNotNull} clauses matter: the vision service posts rows with
     * only a person count, so a plain "latest row" query would return a row whose
     * temperature is null and the dashboard would blank the temperature tile every
     * time a detection happened to arrive last.
     */
    Optional<RoomData> findFirstByRoomIdAndTemperatureIsNotNullOrderByRecordedAtDesc(String roomId);

    Optional<RoomData> findFirstByRoomIdAndPersonCountIsNotNullOrderByRecordedAtDesc(String roomId);

    Optional<RoomData> findFirstByRoomIdAndHumidityIsNotNullOrderByRecordedAtDesc(String roomId);

    Optional<RoomData> findFirstByRoomIdAndVentTemperatureIsNotNullOrderByRecordedAtDesc(String roomId);

    /**
     * Latest sample carrying both room and vent temperature.
     *
     * <p>AC state is a comparison between the two (Section 20.3), so both readings
     * must come from the same sample. Comparing a fresh vent reading against a room
     * reading from a different sample would let clock skew and sampling jitter show
     * up as spurious AC transitions.
     */
    Optional<RoomData> findFirstByRoomIdAndTemperatureIsNotNullAndVentTemperatureIsNotNullOrderByRecordedAtDesc(
            String roomId);

    List<RoomData> findByRoomIdAndTemperatureIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(
            String roomId, LocalDateTime since);

    List<RoomData> findByRoomIdAndHumidityIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(
            String roomId, LocalDateTime since);

    List<RoomData> findByRoomIdAndPersonCountIsNotNullAndRecordedAtAfterOrderByRecordedAtAsc(
            String roomId, LocalDateTime since);

    @Query("select distinct d.roomId from RoomData d order by d.roomId")
    List<String> findDistinctRoomIds();
}
