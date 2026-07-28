package com.example.vex360.features.company.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.company.entities.Company;

import jakarta.persistence.LockModeType;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByOwnerUserId(UUID ownerUserId);

    boolean existsByOwnerUserId(UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.ownerUser.id = :ownerUserId")
    Optional<Company> findByOwnerUserIdForUpdate(@Param("ownerUserId") UUID ownerUserId);

    @Modifying
    @Query("UPDATE Company c SET c.storageQuotaBytes = c.storageQuotaBytes + :quotaBytes WHERE c.id = :id")
    int incrementStorageQuota(@Param("id") UUID id, @Param("quotaBytes") Long quotaBytes);
}
