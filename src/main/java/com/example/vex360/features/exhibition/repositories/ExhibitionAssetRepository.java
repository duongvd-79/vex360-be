package com.example.vex360.features.exhibition.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.shared.enums.ExhibitionAssetType;

public interface ExhibitionAssetRepository extends JpaRepository<ExhibitionAsset, UUID> {
    Optional<ExhibitionAsset> findByExhibitionIdAndType(Integer exhibitionId, ExhibitionAssetType type);
}
