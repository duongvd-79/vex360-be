package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.shared.enums.DesignRequestStatus;

import jakarta.persistence.LockModeType;

public interface DesignRequestRepository extends JpaRepository<DesignRequest, UUID> {
    String REQUEST_DETAILS_QUERY = """
            SELECT dr FROM DesignRequest dr
            LEFT JOIN FETCH dr.company
            LEFT JOIN FETCH dr.booth booth
            LEFT JOIN FETCH booth.exhibitorRegistration registration
            LEFT JOIN FETCH registration.exhibitionPackage exhibitionPackage
            LEFT JOIN FETCH exhibitionPackage.exhibition exhibition
            LEFT JOIN FETCH dr.assignedDesigner assignedDesigner
            """;

    List<DesignRequestStatus> SLOT_OCCUPYING_STATUSES = List.of(
            DesignRequestStatus.ASSIGNED,
            DesignRequestStatus.DRAFT_SUBMITTED,
            DesignRequestStatus.REVISION_REQUESTED);

    List<DesignRequestStatus> NON_TERMINAL_STATUSES = List.of(
            DesignRequestStatus.PENDING,
            DesignRequestStatus.ASSIGNED,
            DesignRequestStatus.DRAFT_SUBMITTED,
            DesignRequestStatus.REVISION_REQUESTED);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dr FROM DesignRequest dr WHERE dr.id = :id")
    Optional<DesignRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = REQUEST_DETAILS_QUERY + """
            WHERE dr.company.id = :companyId
              AND (:keyword IS NULL
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(assignedDesigner.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR dr.status = :status)
            """, countQuery = """
            SELECT COUNT(dr) FROM DesignRequest dr
            LEFT JOIN dr.booth booth
            LEFT JOIN booth.exhibitorRegistration registration
            LEFT JOIN registration.exhibitionPackage exhibitionPackage
            LEFT JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN dr.assignedDesigner assignedDesigner
            WHERE dr.company.id = :companyId
              AND (:keyword IS NULL
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(assignedDesigner.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR dr.status = :status)
            """)
    Page<DesignRequest> searchForCompany(
            @Param("companyId") UUID companyId,
            @Param("keyword") String keyword,
            @Param("status") DesignRequestStatus status,
            Pageable pageable);

    @Query(value = REQUEST_DETAILS_QUERY + """
            WHERE (:keyword IS NULL
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(dr.company.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(assignedDesigner.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR dr.status = :status)
            """, countQuery = """
            SELECT COUNT(dr) FROM DesignRequest dr
            LEFT JOIN dr.booth booth
            LEFT JOIN booth.exhibitorRegistration registration
            LEFT JOIN registration.exhibitionPackage exhibitionPackage
            LEFT JOIN exhibitionPackage.exhibition exhibition
            LEFT JOIN dr.assignedDesigner assignedDesigner
            WHERE (:keyword IS NULL
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(dr.company.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(assignedDesigner.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR dr.status = :status)
            """)
    Page<DesignRequest> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") DesignRequestStatus status,
            Pageable pageable);

    @Query(value = REQUEST_DETAILS_QUERY + """
            WHERE dr.assignedDesigner.id = :designerId
              AND (:status IS NULL OR dr.status = :status)
            """, countQuery = """
            SELECT COUNT(dr) FROM DesignRequest dr
            WHERE dr.assignedDesigner.id = :designerId
              AND (:status IS NULL OR dr.status = :status)
            """)
    Page<DesignRequest> searchForDesigner(
            @Param("designerId") UUID designerId,
            @Param("status") DesignRequestStatus status,
            Pageable pageable);

    long countByBoothIdAndQuotaChargedTrue(UUID boothId);

    boolean existsByBoothIdAndStatusIn(UUID boothId, List<DesignRequestStatus> statuses);

    List<DesignRequest> findByBoothIdAndStatusAndIdNot(
            UUID boothId,
            DesignRequestStatus status,
            UUID id);

    @Query("""
            SELECT COALESCE(SUM(dr.reviewCount), 0)
            FROM DesignRequest dr
            WHERE dr.booth.id = :boothId
            """)
    long sumReviewCountByBoothId(@Param("boothId") UUID boothId);

    long countByAssignedDesignerIdAndStatusIn(UUID designerId, List<DesignRequestStatus> statuses);

    long countByStatus(DesignRequestStatus status);

    long countByStatusIn(List<DesignRequestStatus> statuses);

    @Query("SELECT dr.status, COUNT(dr) FROM DesignRequest dr GROUP BY dr.status")
    List<Object[]> countGroupedByStatus();

    @Query("""
            SELECT FUNCTION('DATE', dr.createdAt), COUNT(dr)
            FROM DesignRequest dr
            WHERE dr.createdAt BETWEEN :start AND :end
            GROUP BY FUNCTION('DATE', dr.createdAt)
            ORDER BY FUNCTION('DATE', dr.createdAt)
            """)
    List<Object[]> aggregateDailyCreated(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT dr.assignedDesigner.id, dr.assignedDesigner.fullName, dr.assignedDesigner.email, COUNT(dr)
            FROM DesignRequest dr
            WHERE dr.status IN :statuses
              AND dr.assignedDesigner IS NOT NULL
            GROUP BY dr.assignedDesigner.id, dr.assignedDesigner.fullName, dr.assignedDesigner.email
            """)
    List<Object[]> countActiveRequestsByDesigner(@Param("statuses") List<DesignRequestStatus> statuses);

    @Query("""
            SELECT COUNT(dr) FROM DesignRequest dr
            WHERE dr.status = :status
              AND (:mode IS NULL OR dr.mode = :mode)
            """)
    long countFiltered(
            @Param("status") DesignRequestStatus status,
            @Param("mode") DesignRequestMode mode);

    @Query("""
            SELECT COUNT(dr) FROM DesignRequest dr
            WHERE dr.status IN :statuses
              AND (:mode IS NULL OR dr.mode = :mode)
            """)
    long countFilteredIn(
            @Param("statuses") List<DesignRequestStatus> statuses,
            @Param("mode") DesignRequestMode mode);

    @Query("""
            SELECT COUNT(dr) FROM DesignRequest dr
            WHERE dr.assignedDesigner.id = :designerId
              AND dr.status IN :statuses
              AND (:mode IS NULL OR dr.mode = :mode)
            """)
    long countDesignerFilteredIn(
            @Param("designerId") UUID designerId,
            @Param("statuses") List<DesignRequestStatus> statuses,
            @Param("mode") DesignRequestMode mode);
}
