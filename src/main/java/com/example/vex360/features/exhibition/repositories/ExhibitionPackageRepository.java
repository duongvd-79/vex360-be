package com.example.vex360.features.exhibition.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;

import java.util.List;
import java.util.UUID;
import com.example.vex360.features.exhibition.entities.Exhibition;

public interface ExhibitionPackageRepository extends JpaRepository<ExhibitionPackage, Integer> {
    List<ExhibitionPackage> findByExhibitionId(Integer exhibitionId);
    List<ExhibitionPackage> findByExhibition(Exhibition exhibition);
    java.util.Optional<ExhibitionPackage> findByExhibitionIdAndTemplateId(Integer exhibitionId, UUID templateId);
}
