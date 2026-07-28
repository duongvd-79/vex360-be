package com.example.vex360.features.exhibition.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import jakarta.persistence.LockModeType;

public interface ExhibitorRegistrationRepository extends JpaRepository<ExhibitorRegistration, Integer> {
    Optional<ExhibitorRegistration> findByUuid(UUID uuid);

    boolean existsByExhibitionPackageExhibitionId(Integer exhibitionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExhibitorRegistration r WHERE r.uuid = :uuid")
    Optional<ExhibitorRegistration> findByUuidForUpdate(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExhibitorRegistration r WHERE r.id = :id")
    Optional<ExhibitorRegistration> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByExhibitionPackageId(Integer exhibitionPackageId);

    @Query("SELECT COUNT(r) > 0 FROM ExhibitorRegistration r " +
            "WHERE r.company.id = :companyId " +
            "AND r.exhibitionPackage.exhibition.id = :exhibitionId " +
            "AND r.status IN :statuses")
    boolean existsActiveRegistration(
            @Param("companyId") UUID companyId,
            @Param("exhibitionId") Integer exhibitionId,
            @Param("statuses") Collection<ExhibitorRegistrationStatus> statuses);

    @Query("""
            SELECT COUNT(r) > 0
            FROM ExhibitorRegistration r
            WHERE r.exhibitionPackage.exhibition.id = :exhibitionId
              AND (
                r.status IN :unresolvedStatuses
                OR (
                  r.status = :approvedStatus
                  AND NOT EXISTS (
                    SELECT b.id
                    FROM Booth b
                    WHERE b.exhibitorRegistration = r
                      AND b.isTemplate = false
                      AND b.status = :publishedBoothStatus
                  )
                )
              )
            """)
    boolean existsPublicationBlocker(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("unresolvedStatuses") Collection<ExhibitorRegistrationStatus> unresolvedStatuses,
            @Param("approvedStatus") ExhibitorRegistrationStatus approvedStatus,
            @Param("publishedBoothStatus") BoothStatus publishedBoothStatus);

    @Query(value = "SELECT r FROM ExhibitorRegistration r " +
            "LEFT JOIN FETCH r.company c " +
            "LEFT JOIN FETCH c.ownerUser " +
            "LEFT JOIN FETCH r.exhibitionPackage p " +
            "LEFT JOIN FETCH p.template " +
            "LEFT JOIN FETCH p.exhibition e " +
            "LEFT JOIN FETCH r.reviewedBy " +
            "WHERE e.organizer.id = :organizerId " +
            "AND (:exhibitionUuid IS NULL OR e.uuid = :exhibitionUuid) " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(r) FROM ExhibitorRegistration r "
                    +
                    "WHERE r.exhibitionPackage.exhibition.organizer.id = :organizerId " +
                    "AND (:exhibitionUuid IS NULL OR r.exhibitionPackage.exhibition.uuid = :exhibitionUuid) "
                    +
                    "AND (:status IS NULL OR r.status = :status) " +
                    "AND (:keyword IS NULL OR LOWER(r.company.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.exhibitionPackage.exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExhibitorRegistration> searchForOrganizer(
            @Param("organizerId") UUID organizerId,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("status") ExhibitorRegistrationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = "SELECT r FROM ExhibitorRegistration r " +
            "LEFT JOIN FETCH r.company c " +
            "LEFT JOIN FETCH c.ownerUser " +
            "LEFT JOIN FETCH r.exhibitionPackage p " +
            "LEFT JOIN FETCH p.template " +
            "LEFT JOIN FETCH p.exhibition e " +
            "LEFT JOIN FETCH r.reviewedBy " +
            "WHERE r.company.id = :companyId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(r) FROM ExhibitorRegistration r "
                    +
                    "WHERE r.company.id = :companyId " +
                    "AND (:status IS NULL OR r.status = :status) " +
                    "AND (:keyword IS NULL OR LOWER(r.exhibitionPackage.exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExhibitorRegistration> searchForExhibitor(
            @Param("companyId") UUID companyId,
            @Param("status") ExhibitorRegistrationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT r.packageNameSnapshot, COUNT(r), COALESCE(SUM(r.finalPriceSnapshot), 0)
            FROM ExhibitorRegistration r
            WHERE r.exhibitionPackage.exhibition.id = :exhibitionId
              AND r.status = :status
            GROUP BY r.packageNameSnapshot
            """)
    List<Object[]> aggregatePackageSales(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("status") ExhibitorRegistrationStatus status);

        /**
         * Đếm số đơn đăng ký gian hàng ở một trạng thái của triển lãm — dùng để tính
         * tỷ lệ lấp đầy gian hàng (đã duyệt / dự kiến) ở dashboard ban tổ chức.
         */
        long countByExhibitionPackageExhibitionIdAndStatus(
                        Integer exhibitionId,
                        ExhibitorRegistrationStatus status);

        @Query("""
                SELECT exhibition.id, COUNT(registration)
                FROM ExhibitorRegistration registration
                JOIN registration.exhibitionPackage exhibitionPackage
                JOIN exhibitionPackage.exhibition exhibition
                WHERE exhibition.id IN :exhibitionIds
                  AND registration.status = :status
                GROUP BY exhibition.id
                """)
        List<Object[]> countByStatusGroupedByExhibition(
                        @Param("exhibitionIds") List<Integer> exhibitionIds,
                        @Param("status") ExhibitorRegistrationStatus status);

        @Query("""
                SELECT FUNCTION('DATE', registration.submittedAt), COUNT(registration)
                FROM ExhibitorRegistration registration
                WHERE registration.exhibitionPackage.exhibition.id IN :exhibitionIds
                  AND registration.submittedAt BETWEEN :startDateTime AND :endDateTime
                GROUP BY FUNCTION('DATE', registration.submittedAt)
                ORDER BY FUNCTION('DATE', registration.submittedAt)
                """)
        List<Object[]> aggregateDailySubmissions(
                        @Param("exhibitionIds") List<Integer> exhibitionIds,
                        @Param("startDateTime") java.time.Instant startDateTime,
                        @Param("endDateTime") java.time.Instant endDateTime);

}
