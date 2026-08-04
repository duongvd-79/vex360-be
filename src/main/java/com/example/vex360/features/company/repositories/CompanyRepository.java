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

    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE c.logoUrl LIKE CONCAT('%/', :publicId, '.%') OR c.logoUrl LIKE CONCAT('%/', :publicId)")
    boolean existsByLogoUrlContaining(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.ownerUser.id = :ownerUserId")
    Optional<Company> findByOwnerUserIdForUpdate(@Param("ownerUserId") UUID ownerUserId);

    /**
     * Atomically creates the initial company row without changing an existing
     * profile. The unique owner_user_id constraint serializes concurrent
     * requests; a duplicate becomes a no-op and the service reads the winner.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO companies (
                id,
                owner_user_id,
                name,
                email,
                phone,
                status,
                storage_quota_bytes,
                storage_used_bytes,
                storage_reserved_bytes
            ) VALUES (
                UUID_TO_BIN(:companyId),
                UUID_TO_BIN(:ownerUserId),
                :name,
                :email,
                :phone,
                :status,
                524288000,
                0,
                0
            )
            ON DUPLICATE KEY UPDATE owner_user_id = owner_user_id
            """, nativeQuery = true)
    int insertCompanyIfAbsent(
            @Param("companyId") String companyId,
            @Param("ownerUserId") String ownerUserId,
            @Param("name") String name,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("status") String status);

    @Modifying
    @Query("UPDATE Company c SET c.storageQuotaBytes = c.storageQuotaBytes + :quotaBytes WHERE c.id = :id")
    int incrementStorageQuota(@Param("id") UUID id, @Param("quotaBytes") Long quotaBytes);
}
