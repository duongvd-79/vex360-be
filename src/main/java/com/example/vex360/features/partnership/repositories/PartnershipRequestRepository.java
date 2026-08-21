package com.example.vex360.features.partnership.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;

public interface PartnershipRequestRepository extends JpaRepository<PartnershipRequest, UUID> {
    boolean existsByRequesterEmailAndStatus(String requesterEmail, PartnershipRequestStatus status);

    boolean existsBySubmittedByUserIdAndStatus(UUID submittedByUserId, PartnershipRequestStatus status);

    boolean existsByActiveRequesterEmail(String activeRequesterEmail);

    boolean existsByActiveSubmittedByUserId(UUID activeSubmittedByUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PartnershipRequest pr WHERE pr.id = :id")
    java.util.Optional<PartnershipRequest> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(PartnershipRequestStatus status);

    @Query("""
            SELECT pr FROM PartnershipRequest pr
            WHERE (:keyword IS NULL
                OR LOWER(pr.organizationName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(pr.requesterName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(pr.requesterEmail) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR pr.status = :status)
              AND (:requestedRole IS NULL OR pr.requestedRole = :requestedRole)
              AND (:startInstant IS NULL OR pr.createdAt >= :startInstant)
              AND (:endInstant IS NULL OR pr.createdAt <= :endInstant)
            """)
    Page<PartnershipRequest> searchRequests(
            @Param("keyword") String keyword,
            @Param("status") PartnershipRequestStatus status,
            @Param("requestedRole") Role requestedRole,
            @Param("startInstant") java.time.Instant startInstant,
            @Param("endInstant") java.time.Instant endInstant,
            Pageable pageable);
}
