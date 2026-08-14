package com.example.vex360.features.booth.repositories;

import java.util.List;
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
import com.example.vex360.shared.enums.BoothListingPriority;

import jakarta.persistence.LockModeType;

public interface BoothRepository extends JpaRepository<Booth, UUID> {
    boolean existsByThumbnailPublicIdOrBackgroundMusicPublicId(
            String thumbnailPublicId,
            String backgroundMusicPublicId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id AND b.isTemplate = true
            """)
    Optional<Booth> findTemplateByIdForUpdate(@Param("id") UUID id);

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
            LEFT JOIN company.ownerUser ownerUser
            WHERE exhibition.uuid = :exhibitionUuid
              AND exhibition.organizer.id = :organizerId
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(company.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(ownerUser.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
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
            SELECT DISTINCT b FROM Booth b
            JOIN FETCH b.exhibitorRegistration registration
            JOIN FETCH registration.exhibitionPackage exhibitionPackage
            JOIN FETCH exhibitionPackage.exhibition exhibition
            LEFT JOIN FETCH b.company
            LEFT JOIN FETCH b.panoramas
            WHERE b.id = :id
              AND b.isTemplate = false
              AND exhibition.uuid = :exhibitionUuid
            """)
    Optional<Booth> findDetailForAdmin(
            @Param("id") UUID id,
            @Param("exhibitionUuid") UUID exhibitionUuid);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN b.company company
            LEFT JOIN company.ownerUser ownerUser
            WHERE exhibition.uuid = :exhibitionUuid
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(company.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(ownerUser.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR b.status = :status)
            ORDER BY CASE WHEN b.status = 'PENDING' THEN 1 WHEN b.status = 'PUBLISHED' THEN 2 ELSE 3 END ASC, b.updatedAt DESC
            """)
    Page<Booth> searchForAdmin(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("keyword") String keyword,
            @Param("status") BoothStatus status,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN b.company company
            WHERE exhibition.uuid = :exhibitionUuid
              AND (COALESCE(b.warningCount, 0) > 0 OR b.bannedAt IS NOT NULL)
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(company.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY COALESCE(b.bannedAt, b.warnedAt) DESC
            """)
    Page<Booth> searchModeratedForAdmin(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("keyword") String keyword,
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
            SELECT COUNT(b) FROM Booth b
            WHERE b.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND b.isTemplate = false
            """)
    long countBoothsByExhibitionId(@Param("exhibitionId") Integer exhibitionId);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND b.isTemplate = false
            """)
    List<Booth> findBoothsByExhibitionId(@Param("exhibitionId") Integer exhibitionId);

    @Query("""
            SELECT e.id, COUNT(b)
            FROM Booth b
            JOIN b.exhibitorRegistration r
            JOIN r.exhibitionPackage p
            JOIN p.exhibition e
            WHERE b.isTemplate = false AND e.id IN :ids
            GROUP BY e.id
            """)
    List<Object[]> countBoothsGroupedByExhibition(@Param("ids") List<Integer> ids);

    @Query("""
            SELECT b.status, COUNT(b)
            FROM Booth b
            WHERE b.isTemplate = false
            GROUP BY b.status
            """)
    List<Object[]> countBoothsGroupedByStatus();

    @Query("""
            SELECT e.id, COUNT(b)
            FROM Booth b
            JOIN b.exhibitorRegistration r
            JOIN r.exhibitionPackage p
            JOIN p.exhibition e
            WHERE b.isTemplate = false
              AND b.status = :status
              AND e.id IN :exhibitionIds
            GROUP BY e.id
            """)
    List<Object[]> countBoothsGroupedByExhibitionAndStatus(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("status") BoothStatus status);

    @Query("""
            SELECT b FROM Booth b
            JOIN b.exhibitorRegistration reg
            JOIN reg.exhibitionPackage pkg
            JOIN pkg.exhibition exh
            WHERE exh.uuid = :exhibitionUuid
              AND b.status = :boothStatus
              AND b.isTemplate = false
              AND (:keyword IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:listingPriority IS NULL
                OR reg.listingPrioritySnapshot = :listingPriority
                OR (reg.listingPrioritySnapshot IS NULL AND pkg.listingPrioritySnapshot = :listingPriority))
            ORDER BY CASE
                WHEN reg.listingPrioritySnapshot = 'FEATURED'
                  OR (reg.listingPrioritySnapshot IS NULL AND pkg.listingPrioritySnapshot = 'FEATURED') THEN 1
                WHEN reg.listingPrioritySnapshot = 'PRIORITY'
                  OR (reg.listingPrioritySnapshot IS NULL AND pkg.listingPrioritySnapshot = 'PRIORITY') THEN 2
                ELSE 3
              END ASC,
              b.updatedAt DESC,
              b.id ASC
            """)
    Page<Booth> findPublishedBoothsByExhibitionUuid(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("boothStatus") BoothStatus boothStatus,
            @Param("keyword") String keyword,
            @Param("listingPriority") BoothListingPriority listingPriority,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            JOIN FETCH b.exhibitorRegistration reg
            JOIN FETCH reg.exhibitionPackage pkg
            JOIN FETCH pkg.exhibition exh
            LEFT JOIN FETCH pkg.template
            LEFT JOIN FETCH b.company
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
