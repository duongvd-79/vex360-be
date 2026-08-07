package com.example.vex360.features.wallet.repositories;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.enums.WithdrawalStatus;

import jakarta.persistence.LockModeType;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

    Optional<WithdrawalRequest> findByUuid(UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WithdrawalRequest w WHERE w.uuid = :uuid")
    Optional<WithdrawalRequest> findWithLockByUuid(@Param("uuid") UUID uuid);

    boolean existsByCompanyIdAndStatusIn(UUID companyId, Collection<WithdrawalStatus> statuses);

    boolean existsByTransferReference(String transferReference);

    @Query("SELECT COUNT(w) > 0 FROM WithdrawalRequest w WHERE w.proofUrl LIKE CONCAT('%/', :publicId, '.%') OR w.proofUrl LIKE CONCAT('%/', :publicId)")
    boolean existsByProofUrlContaining(@Param("publicId") String publicId);

    Page<WithdrawalRequest> findByCompanyId(UUID companyId, Pageable pageable);

    @Query("SELECT w FROM WithdrawalRequest w WHERE (:status IS NULL OR w.status = :status) AND (:companyId IS NULL OR w.company.id = :companyId)")
    Page<WithdrawalRequest> findByAdminFilter(
            @Param("status") WithdrawalStatus status,
            @Param("companyId") UUID companyId,
            Pageable pageable);
}
