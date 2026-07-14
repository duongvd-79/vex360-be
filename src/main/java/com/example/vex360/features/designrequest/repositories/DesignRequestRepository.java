package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.shared.enums.DesignRequestStatus;

@Repository
public interface DesignRequestRepository extends JpaRepository<DesignRequest, UUID> {
    List<DesignRequestStatus> ACTIVE_STATUSES = List.of(
            DesignRequestStatus.ASSIGNED,
            DesignRequestStatus.DRAFT_SUBMITTED,
            DesignRequestStatus.REVISION_REQUESTED);

    List<DesignRequestStatus> OPEN_STATUSES = List.of(
            DesignRequestStatus.PENDING,
            DesignRequestStatus.ASSIGNED,
            DesignRequestStatus.DRAFT_SUBMITTED,
            DesignRequestStatus.REVISION_REQUESTED);

    @Query("""
            SELECT dr FROM DesignRequest dr
            WHERE dr.company.id = :companyId
              AND (:status IS NULL OR dr.status = :status)
            """)
    Page<DesignRequest> searchForCompany(
            @Param("companyId") UUID companyId,
            @Param("status") DesignRequestStatus status,
            Pageable pageable);

    @Query("""
            SELECT dr FROM DesignRequest dr
            WHERE (:status IS NULL OR dr.status = :status)
              AND (:designerId IS NULL OR dr.assignedDesigner.id = :designerId)
            """)
    Page<DesignRequest> searchForAdmin(
            @Param("status") DesignRequestStatus status,
            @Param("designerId") UUID designerId,
            Pageable pageable);

    @Query("""
            SELECT dr FROM DesignRequest dr
            WHERE dr.assignedDesigner.id = :designerId
              AND (:status IS NULL OR dr.status = :status)
            """)
    Page<DesignRequest> searchForDesigner(
            @Param("designerId") UUID designerId,
            @Param("status") DesignRequestStatus status,
            Pageable pageable);

    long countByBoothId(UUID boothId);

    boolean existsByBoothIdAndStatusIn(UUID boothId, List<DesignRequestStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(dr.reviewCount), 0)
            FROM DesignRequest dr
            WHERE dr.booth.id = :boothId
            """)
    long sumReviewCountByBoothId(@Param("boothId") UUID boothId);

    long countByAssignedDesignerIdAndStatusIn(UUID designerId, List<DesignRequestStatus> statuses);

    long countByStatus(DesignRequestStatus status);

    long countByStatusIn(List<DesignRequestStatus> statuses);

    @Query("""
            SELECT dr.assignedDesigner.id, dr.assignedDesigner.fullName, dr.assignedDesigner.email, COUNT(dr)
            FROM DesignRequest dr
            WHERE dr.status IN :statuses
              AND dr.assignedDesigner IS NOT NULL
            GROUP BY dr.assignedDesigner.id, dr.assignedDesigner.fullName, dr.assignedDesigner.email
            """)
    List<Object[]> countActiveRequestsByDesigner(@Param("statuses") List<DesignRequestStatus> statuses);
}
