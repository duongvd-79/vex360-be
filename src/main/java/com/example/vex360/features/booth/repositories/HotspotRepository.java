package com.example.vex360.features.booth.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.booth.enums.BoothStatus;

public interface HotspotRepository extends JpaRepository<Hotspot, UUID> {
    @Query(value = """
            SELECT DISTINCT product
            FROM Hotspot hotspot
            JOIN hotspot.product product
            JOIN hotspot.sourcePanorama panorama
            JOIN panorama.booth booth
            JOIN booth.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE exhibition.uuid = :exhibitionUuid
              AND booth.status = :boothStatus
              AND booth.isTemplate = false
              AND product.status = :productStatus
              AND (:keyword IS NULL
                OR LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(product.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """, countQuery = """
            SELECT COUNT(DISTINCT product.id)
            FROM Hotspot hotspot
            JOIN hotspot.product product
            JOIN hotspot.sourcePanorama panorama
            JOIN panorama.booth booth
            JOIN booth.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE exhibition.uuid = :exhibitionUuid
              AND booth.status = :boothStatus
              AND booth.isTemplate = false
              AND product.status = :productStatus
              AND (:keyword IS NULL
                OR LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(product.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> searchDisplayedProductsForVisitor(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("keyword") String keyword,
            @Param("productStatus") ProductStatus productStatus,
            @Param("boothStatus") BoothStatus boothStatus,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT product
            FROM Hotspot hotspot
            JOIN hotspot.product product
            JOIN FETCH product.company
            JOIN FETCH product.category
            LEFT JOIN FETCH product.contents
            JOIN hotspot.sourcePanorama panorama
            JOIN panorama.booth booth
            JOIN booth.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE exhibition.uuid = :exhibitionUuid
              AND product.id = :productId
              AND product.status = :productStatus
              AND booth.status = :boothStatus
              AND booth.isTemplate = false
            """)
    Optional<Product> findDisplayedProductDetailForVisitor(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("productId") UUID productId,
            @Param("productStatus") ProductStatus productStatus,
            @Param("boothStatus") BoothStatus boothStatus);

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
            SELECT product.id AS productId,
                   booth.id AS boothId,
                   booth.name AS boothName,
                   booth.thumbnailUrl AS boothThumbnailUrl,
                   registration.listingPrioritySnapshot AS listingPrioritySnapshot,
                   template.listingPriority AS templateListingPriority,
                   panorama.id AS panoramaId,
                   panorama.name AS panoramaName,
                   hotspot.id AS hotspotId
            FROM Hotspot hotspot
            JOIN hotspot.product product
            JOIN hotspot.sourcePanorama panorama
            JOIN panorama.booth booth
            JOIN booth.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.template template
            JOIN exhibitionPackage.exhibition exhibition
            WHERE exhibition.uuid = :exhibitionUuid
              AND product.id IN :productIds
              AND product.status = :productStatus
              AND booth.status = :boothStatus
              AND booth.isTemplate = false
            ORDER BY product.id ASC,
              CASE
                WHEN registration.listingPrioritySnapshot = 'FEATURED'
                  OR (registration.listingPrioritySnapshot IS NULL AND template.listingPriority = 'FEATURED') THEN 1
                WHEN registration.listingPrioritySnapshot = 'PRIORITY'
                  OR (registration.listingPrioritySnapshot IS NULL AND template.listingPriority = 'PRIORITY') THEN 2
                ELSE 3
              END ASC,
              booth.name ASC,
              panorama.isDefault DESC,
              panorama.orderIndex ASC,
              hotspot.id ASC
            """)
    List<ProductPlacementProjection> findProductPlacements(
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("productIds") Collection<UUID> productIds,
            @Param("productStatus") ProductStatus productStatus,
            @Param("boothStatus") BoothStatus boothStatus);

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
