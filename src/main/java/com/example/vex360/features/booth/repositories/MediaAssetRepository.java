package com.example.vex360.features.booth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.vex360.features.booth.entities.MediaAsset;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Page<MediaAsset> findByCompanyId(UUID companyId, Pageable pageable);

    Optional<MediaAsset> findByIdAndCompanyId(UUID id, UUID companyId);
}
