package com.example.vex360.features.booth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;

@Repository
public interface BoothReviewRequestRepository extends JpaRepository<BoothReviewRequest, UUID> {
    boolean existsByBoothIdAndStatus(UUID boothId, BoothReviewStatus status);

    Page<BoothReviewRequest> findByBoothIdOrderBySubmittedAtDesc(UUID boothId, Pageable pageable);

    @Query("""
            SELECT r FROM BoothReviewRequest r
            JOIN r.booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN b.company company
            WHERE exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
              AND (:status IS NULL OR r.status = :status)
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(company.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<BoothReviewRequest> searchForOrganizer(
            @Param("organizerId") UUID organizerId,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("status") BoothReviewStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT r FROM BoothReviewRequest r
            JOIN r.booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE r.id = :requestId
              AND exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
            """)
    Optional<BoothReviewRequest> findForOrganizer(
            @Param("organizerId") UUID organizerId,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("requestId") UUID requestId);
}
