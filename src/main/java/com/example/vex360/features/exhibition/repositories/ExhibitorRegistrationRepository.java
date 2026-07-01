package com.example.vex360.features.exhibition.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.ExhibitorRegistration;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExhibitorRegistrationRepository extends JpaRepository<ExhibitorRegistration, Integer> {
    Optional<ExhibitorRegistration> findByUuid(UUID uuid);
    boolean existsByExhibitionPackageExhibitionId(Integer exhibitionId);
    boolean existsByExhibitionPackageId(Integer exhibitionPackageId);
}
