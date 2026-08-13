package com.smartroom.backend.repository;

import com.smartroom.backend.domain.AcStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AcStatusRepository extends JpaRepository<AcStatus, Long> {

    /** The interval currently running: at most one open row per room. */
    Optional<AcStatus> findFirstByRoomIdAndEndTimeIsNullOrderByStartTimeDesc(String roomId);

    Optional<AcStatus> findFirstByRoomIdOrderByStartTimeDesc(String roomId);

    List<AcStatus> findByRoomIdAndStartTimeAfterOrderByStartTimeDesc(String roomId, LocalDateTime since);

    List<AcStatus> findByRoomIdOrderByStartTimeDesc(String roomId, Pageable pageable);
}
