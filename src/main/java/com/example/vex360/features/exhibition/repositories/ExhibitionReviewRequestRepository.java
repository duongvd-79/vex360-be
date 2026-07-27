package com.example.vex360.features.exhibition.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.exhibition.entities.ExhibitionReviewRequest;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;

import jakarta.persistence.LockModeType;

public interface ExhibitionReviewRequestRepository extends JpaRepository<ExhibitionReviewRequest, UUID> {

    @Query("SELECT r FROM ExhibitionReviewRequest r LEFT JOIN FETCH r.submittedBy LEFT JOIN FETCH r.reviewedBy WHERE r.exhibition.uuid = :exhibitionUuid ORDER BY r.versionNumber DESC")
    List<ExhibitionReviewRequest> findByExhibitionUuidOrderByVersionNumberDesc(
            @Param("exhibitionUuid") UUID exhibitionUuid);

    List<ExhibitionReviewRequest> findByExhibitionIdOrderByVersionNumberDesc(Integer exhibitionId);

    Optional<ExhibitionReviewRequest> findFirstByExhibitionIdOrderByVersionNumberDesc(Integer exhibitionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExhibitionReviewRequest r WHERE r.exhibition.id = :exhibitionId AND r.status = :status")
    Optional<ExhibitionReviewRequest> findFirstByExhibitionIdAndStatusForUpdate(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("status") ExhibitionReviewStatus status);

    Optional<ExhibitionReviewRequest> findFirstByExhibitionIdAndStatus(
            Integer exhibitionId,
            ExhibitionReviewStatus status);
}
