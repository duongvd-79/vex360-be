package com.example.vex360.features.booth.repositories;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.entities.Panorama;

import jakarta.persistence.LockModeType;

public interface PanoramaRepository extends JpaRepository<Panorama, UUID> {
    List<Panorama> findByBoothIdOrderByOrderIndexAsc(UUID boothId);

    @Query("""
            SELECT DISTINCT p FROM Panorama p
            LEFT JOIN FETCH p.hotspots hotspot
            LEFT JOIN FETCH hotspot.targetPanorama
            LEFT JOIN FETCH hotspot.product
            LEFT JOIN FETCH hotspot.mediaAsset mediaAsset
            LEFT JOIN FETCH mediaAsset.company
            WHERE p.booth.id = :boothId
            ORDER BY p.orderIndex ASC
            """)
    List<Panorama> findDetailsByBoothId(@Param("boothId") UUID boothId);

    long countByBoothId(UUID boothId);

    boolean existsByImageKey(String imageKey);

    @Query("""
            SELECT DISTINCT p.imageKey
            FROM Panorama p
            WHERE p.imageKey IN :imageKeys
            """)
    List<String> findUsedImageKeys(@Param("imageKeys") Set<String> imageKeys);

    @Query("""
            SELECT p.booth.id AS boothId, COUNT(p.id) AS contentCount
            FROM Panorama p
            WHERE p.booth.id IN :boothIds
            GROUP BY p.booth.id
            """)
    List<BoothContentCountProjection> countByBoothIds(@Param("boothIds") List<UUID> boothIds);

    Optional<Panorama> findByIdAndBoothId(UUID id, UUID boothId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Panorama p
            WHERE p.id = :id AND p.booth.id = :boothId
            """)
    Optional<Panorama> findByIdAndBoothIdForUpdate(
            @Param("id") UUID id,
            @Param("boothId") UUID boothId);

    @Modifying
    @Query("""
            UPDATE Panorama p
            SET p.isDefault = false
            WHERE p.booth.id = :boothId
            """)
    void clearDefaultForBooth(@Param("boothId") UUID boothId);
}
