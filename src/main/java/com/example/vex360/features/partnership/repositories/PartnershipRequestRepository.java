package com.example.vex360.features.partnership.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;

@Repository
public interface PartnershipRequestRepository extends JpaRepository<PartnershipRequest, UUID> {
    boolean existsByRequesterEmailAndStatus(String requesterEmail, PartnershipRequestStatus status);

    boolean existsBySubmittedByUserIdAndStatus(UUID submittedByUserId, PartnershipRequestStatus status);

    boolean existsByActiveRequesterEmail(String activeRequesterEmail);

    boolean existsByActiveSubmittedByUserId(UUID activeSubmittedByUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PartnershipRequest pr WHERE pr.id = :id")
    java.util.Optional<PartnershipRequest> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PartnershipRequest pr WHERE pr.verificationTokenHash = :tokenHash")
    java.util.Optional<PartnershipRequest> findByVerificationTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByStatus(PartnershipRequestStatus status);

    int deleteByStatusAndCreatedAtBefore(PartnershipRequestStatus status, java.time.LocalDateTime dateTime);

    int deleteByStatusAndReviewedAtBefore(PartnershipRequestStatus status, java.time.LocalDateTime dateTime);

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
