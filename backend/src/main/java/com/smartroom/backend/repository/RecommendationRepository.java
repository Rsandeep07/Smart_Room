package com.smartroom.backend.repository;

import com.smartroom.backend.domain.Recommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Optional<Recommendation> findFirstByRoomIdOrderByCreatedAtDesc(String roomId);

    List<Recommendation> findByRoomIdAndCreatedAtAfterOrderByCreatedAtDesc(String roomId, LocalDateTime since);

    List<Recommendation> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
