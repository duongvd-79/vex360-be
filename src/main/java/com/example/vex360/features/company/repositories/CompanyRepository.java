package com.example.vex360.features.company.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.company.entities.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByOwnerUserId(UUID ownerUserId);

    boolean existsByOwnerUserId(UUID ownerUserId);
}
