package com.smartroom.backend.repository;

import com.smartroom.backend.domain.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** The alert the dashboard banner should currently show, if any. */
    Optional<Alert> findFirstByRoomIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(String roomId);

    /**
     * Active alert of one specific type.
     *
     * <p>Auto-resolution is type-scoped so that clearing the cold-room condition
     * cannot silently dismiss some future alert of an unrelated type.
     */
    Optional<Alert> findFirstByRoomIdAndAlertTypeAndAcknowledgedAtIsNullOrderByCreatedAtDesc(
            String roomId, String alertType);

    /** Guards re-raising the same alert every time the 60-second monitor ticks. */
    boolean existsByRoomIdAndAlertTypeAndAcStatusId(String roomId, String alertType, Long acStatusId);

    List<Alert> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    List<Alert> findByRoomIdAndCreatedAtAfterOrderByCreatedAtDesc(String roomId, LocalDateTime since);
}
