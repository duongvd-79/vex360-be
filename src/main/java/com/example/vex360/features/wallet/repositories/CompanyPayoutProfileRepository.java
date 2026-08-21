package com.example.vex360.features.wallet.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.wallet.entities.CompanyPayoutProfile;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;

public interface CompanyPayoutProfileRepository extends JpaRepository<CompanyPayoutProfile, Long> {

    Optional<CompanyPayoutProfile> findByCompanyId(UUID companyId);

    Page<CompanyPayoutProfile> findByStatus(PayoutProfileStatus status, Pageable pageable);
}
