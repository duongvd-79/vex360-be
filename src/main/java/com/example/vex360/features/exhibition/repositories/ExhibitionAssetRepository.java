package com.example.vex360.features.exhibition.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;

public interface ExhibitionAssetRepository extends JpaRepository<ExhibitionAsset, UUID> {
    boolean existsByPublicId(String publicId);
}
