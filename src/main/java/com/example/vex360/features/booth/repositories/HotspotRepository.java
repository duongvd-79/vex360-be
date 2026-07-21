package com.example.vex360.features.booth.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.product.enums.ProductStatus;

public interface HotspotRepository extends JpaRepository<Hotspot, UUID> {
    List<Hotspot> findBySourcePanoramaIdOrderByNameAsc(UUID sourcePanoramaId);

    Optional<Hotspot> findByIdAndSourcePanoramaId(UUID id, UUID sourcePanoramaId);

    List<Hotspot> findAllByTargetPanoramaIdIn(Collection<UUID> targetPanoramaIds);

    boolean existsByMediaAssetId(UUID mediaAssetId);

    List<Hotspot> findByProduct(Product product);

    @Query("""
            SELECT COUNT(h)
            FROM Hotspot h
            WHERE h.sourcePanorama.booth.id = :boothId
            """)
    long countBySourcePanoramaBoothId(@Param("boothId") UUID boothId);

    boolean existsBySourcePanoramaBoothIdAndTypeNot(UUID boothId, HotspotType type);

    boolean existsBySourcePanoramaBoothIdAndProductStatusNot(UUID boothId, ProductStatus status);

    @Query("""
            SELECT DISTINCT h.product
            FROM Hotspot h
            WHERE h.sourcePanorama.booth.id = :boothId
              AND h.product IS NOT NULL
            """)
    List<Product> findDistinctProductsByBoothId(@Param("boothId") UUID boothId);

    @Query("""
            SELECT h.sourcePanorama.booth.id AS boothId, COUNT(h.id) AS contentCount
            FROM Hotspot h
            WHERE h.sourcePanorama.booth.id IN :boothIds
            GROUP BY h.sourcePanorama.booth.id
            """)
    List<BoothContentCountProjection> countByBoothIds(@Param("boothIds") List<UUID> boothIds);

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

    @Modifying
    @Query("""
            UPDATE Hotspot h
            SET h.targetPanorama = null
            WHERE h.targetPanorama.id IN :panoramaIds
            """)
    void clearTargetsForPanoramas(@Param("panoramaIds") List<UUID> panoramaIds);
}
