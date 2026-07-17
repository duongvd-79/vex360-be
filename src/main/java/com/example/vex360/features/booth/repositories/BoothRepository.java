package com.example.vex360.features.booth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;

import jakarta.persistence.LockModeType;

public interface BoothRepository extends JpaRepository<Booth, UUID> {
    @Query("""
            SELECT b FROM Booth b
            WHERE b.isTemplate = true
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<Booth> searchTemplates(
            @Param("keyword") String keyword,
            @Param("status") BoothStatus status,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id AND b.isTemplate = true
            """)
    Optional<Booth> findTemplateById(@Param("id") UUID id);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.isTemplate = false
              AND b.company.id = :companyId
            """)
    Page<Booth> findCompanyBooths(
            @Param("companyId") UUID companyId,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id
              AND b.isTemplate = false
              AND b.company.id = :companyId
            """)
    Optional<Booth> findCompanyBoothById(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id
              AND b.isTemplate = false
              AND b.company.id = :companyId
            """)
    Optional<Booth> findCompanyBoothByIdForUpdate(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    Optional<Booth> findByExhibitorRegistrationId(Integer exhibitorRegistrationId);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE b.id = :id
              AND b.isTemplate = false
              AND exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
            """)
    Optional<Booth> findForOrganizer(
            @Param("id") UUID id,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("organizerId") UUID organizerId);

    @Query("""
            SELECT DISTINCT b FROM Booth b
            JOIN FETCH b.exhibitorRegistration registration
            JOIN FETCH registration.exhibitionPackage exhibitionPackage
            JOIN FETCH exhibitionPackage.exhibition exhibition
            LEFT JOIN FETCH b.company
            LEFT JOIN FETCH b.panoramas
            WHERE b.id = :id
              AND b.isTemplate = false
              AND exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
            """)
    Optional<Booth> findDetailForOrganizer(
            @Param("id") UUID id,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("organizerId") UUID organizerId);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN b.company company
            WHERE exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(company.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR b.status = :status)
            ORDER BY CASE WHEN b.status = 'PENDING' THEN 1 ELSE 2 END ASC, b.updatedAt DESC
            """)
    Page<Booth> searchForOrganizer(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("organizerId") UUID organizerId,
            @Param("keyword") String keyword,
            @Param("status") BoothStatus status,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration reg
            JOIN reg.exhibitionPackage pkg
            JOIN pkg.exhibition exh
            WHERE exh.uuid = :exhibitionUuid
              AND b.status = :boothStatus
              AND b.isTemplate = false
              AND (:keyword IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Booth> findPublishedBoothsByExhibitionUuid(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("boothStatus") BoothStatus boothStatus,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration reg
            JOIN reg.exhibitionPackage pkg
            JOIN pkg.exhibition exh
            WHERE exh.uuid = :exhibitionUuid
              AND b.id = :boothId
              AND b.status = :boothStatus
              AND b.isTemplate = false
            """)
    Optional<Booth> findPublishedBoothByExhibitionUuidAndBoothId(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("boothId") UUID boothId,
            @Param("boothStatus") BoothStatus boothStatus);
}
