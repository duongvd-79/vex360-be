package com.example.vex360.features.partnership.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.PartnershipRequest;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;

@Repository
public interface PartnershipRequestRepository extends JpaRepository<PartnershipRequest, UUID> {
    boolean existsByRequesterEmailAndStatus(String requesterEmail, PartnershipRequestStatus status);

    boolean existsBySubmittedByUserIdAndStatus(UUID submittedByUserId, PartnershipRequestStatus status);

    @Query("""
            SELECT pr FROM PartnershipRequest pr
            WHERE (:status IS NULL OR pr.status = :status)
              AND (:requestedRole IS NULL OR pr.requestedRole = :requestedRole)
            """)
    Page<PartnershipRequest> searchRequests(
            @Param("status") PartnershipRequestStatus status,
            @Param("requestedRole") Role requestedRole,
            Pageable pageable);
}
