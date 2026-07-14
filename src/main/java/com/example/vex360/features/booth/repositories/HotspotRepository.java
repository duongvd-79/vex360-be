package com.example.vex360.features.booth.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.product.entities.Product;

@Repository
public interface HotspotRepository extends JpaRepository<Hotspot, UUID> {
        List<Hotspot> findBySourcePanoramaIdOrderByNameAsc(UUID sourcePanoramaId);

        Optional<Hotspot> findByIdAndSourcePanoramaId(UUID id, UUID sourcePanoramaId);

        boolean existsByTargetPanoramaId(UUID targetPanoramaId);

        boolean existsByMediaAssetId(UUID mediaAssetId);

        List<Hotspot> findByProduct(Product product);

        @Query("""
                        SELECT COUNT(h)
                        FROM Hotspot h
                        WHERE h.sourcePanorama.booth.id = :boothId
                        """)
        long countBySourcePanoramaBoothId(@Param("boothId") UUID boothId);

        @Query("""
                        SELECT DISTINCT h.product.id
                        FROM Hotspot h
                        WHERE h.sourcePanorama.booth.id = :boothId
                          AND h.product IS NOT NULL
                          AND (:excludedHotspotId IS NULL OR h.id <> :excludedHotspotId)
                        """)
        List<UUID> findDistinctProductIdsByBoothIdExcludingHotspot(
                        @Param("boothId") UUID boothId,
                        @Param("excludedHotspotId") UUID excludedHotspotId);

        @Query("""
                        SELECT DISTINCT h.mediaAsset
                        FROM Hotspot h
                        WHERE h.sourcePanorama.booth.id = :boothId
                          AND h.mediaAsset IS NOT NULL
                          AND (:excludedHotspotId IS NULL OR h.id <> :excludedHotspotId)
                        """)
        List<MediaAsset> findDistinctMediaAssetsByBoothIdExcludingHotspot(
                        @Param("boothId") UUID boothId,
                        @Param("excludedHotspotId") UUID excludedHotspotId);
}
