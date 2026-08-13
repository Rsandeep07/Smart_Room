package com.smartroom.backend.repository;

import com.smartroom.backend.domain.EventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByRoomIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String roomId, LocalDateTime since, Pageable pageable);

    List<EventLog> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
