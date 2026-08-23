package com.example.vex360.features.hall.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.hall.entities.HallReviewRequest;
import com.example.vex360.features.hall.enums.HallReviewStatus;

import jakarta.persistence.LockModeType;

public interface HallReviewRequestRepository extends JpaRepository<HallReviewRequest, UUID> {
    boolean existsByHallIdAndStatus(UUID hallId, HallReviewStatus status);

    long countByHallId(UUID hallId);

    Optional<HallReviewRequest> findTopByHallIdOrderByVersionNumberDescSubmittedAtDesc(UUID hallId);

    Page<HallReviewRequest> findByHallIdOrderBySubmittedAtDesc(UUID hallId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM HallReviewRequest r WHERE r.id = :id")
    Optional<HallReviewRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT r FROM HallReviewRequest r
            JOIN r.hall h
            JOIN h.exhibition e
            WHERE (:status IS NULL OR r.status = :status)
              AND (:keyword IS NULL
                OR LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<HallReviewRequest> searchForAdmin(
            @Param("status") HallReviewStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
