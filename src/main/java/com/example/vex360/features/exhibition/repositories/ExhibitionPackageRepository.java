package com.example.vex360.features.exhibition.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.ExhibitionPackage;

import java.util.List;
import java.util.UUID;
import com.example.vex360.shared.entities.Exhibition;

@Repository
public interface ExhibitionPackageRepository extends JpaRepository<ExhibitionPackage, Integer> {
    List<ExhibitionPackage> findByExhibitionId(Integer exhibitionId);
    List<ExhibitionPackage> findByExhibition(Exhibition exhibition);
    java.util.Optional<ExhibitionPackage> findByExhibitionIdAndTemplateId(Integer exhibitionId, UUID templateId);
}
