package com.example.vex360.features.wallet.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.wallet.entities.OrganizerWallet;

import jakarta.persistence.LockModeType;

public interface OrganizerWalletRepository extends JpaRepository<OrganizerWallet, UUID> {

    Optional<OrganizerWallet> findByCompanyId(UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM OrganizerWallet w WHERE w.company.id = :companyId")
    Optional<OrganizerWallet> findWithLockByCompanyId(@Param("companyId") UUID companyId);
}
