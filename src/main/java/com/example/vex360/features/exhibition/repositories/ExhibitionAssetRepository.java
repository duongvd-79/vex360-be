package com.example.vex360.features.exhibition.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.ExhibitionAsset;
import com.example.vex360.shared.enums.ExhibitionAssetType;

@Repository
public interface ExhibitionAssetRepository extends JpaRepository<ExhibitionAsset, UUID> {
    Optional<ExhibitionAsset> findByExhibitionIdAndType(Integer exhibitionId, ExhibitionAssetType type);
}
