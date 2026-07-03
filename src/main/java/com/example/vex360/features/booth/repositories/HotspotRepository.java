package com.example.vex360.features.booth.repositories;

import java.util.UUID;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Hotspot;

@Repository
public interface HotspotRepository extends JpaRepository<Hotspot, UUID> {
    List<Hotspot> findBySourcePanoramaIdOrderByNameAsc(UUID sourcePanoramaId);

    Optional<Hotspot> findByIdAndSourcePanoramaId(UUID id, UUID sourcePanoramaId);

    boolean existsByTargetPanoramaId(UUID targetPanoramaId);

    boolean existsByMediaAssetId(UUID mediaAssetId);
}
