package com.example.vex360.features.hall.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.enums.HallHotspotType;

import jakarta.persistence.LockModeType;

public interface HallHotspotRepository extends JpaRepository<HallHotspot, UUID> {
    List<HallHotspot> findBySourcePanoramaIdOrderByNameAsc(UUID sourcePanoramaId);

    @Query("""
            SELECT h FROM HallHotspot h
            JOIN FETCH h.sourcePanorama source
            LEFT JOIN FETCH h.targetPanorama
            LEFT JOIN FETCH h.mediaAsset
            LEFT JOIN FETCH h.item
            WHERE source.hall.id = :hallId
            ORDER BY source.orderIndex ASC, h.name ASC, h.id ASC
            """)
    List<HallHotspot> findSnapshotHotspotsByHallId(@Param("hallId") UUID hallId);

    Optional<HallHotspot> findByIdAndSourcePanoramaId(UUID id, UUID sourcePanoramaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HallHotspot h WHERE h.id = :id AND h.sourcePanorama.id = :sourcePanoramaId")
    Optional<HallHotspot> findByIdAndSourcePanoramaIdForUpdate(
            @Param("id") UUID id,
            @Param("sourcePanoramaId") UUID sourcePanoramaId);

    List<HallHotspot> findAllByTargetPanoramaId(UUID targetPanoramaId);

    boolean existsByMediaAssetId(UUID mediaAssetId);

    boolean existsByItemId(UUID itemId);

    @Query("""
            SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
            FROM HallHotspot h
            WHERE h.sourcePanorama.hall.id = :hallId
              AND h.type = :type
              AND h.boothSlotIndex = :slotIndex
              AND (:excludedId IS NULL OR h.id <> :excludedId)
            """)
    boolean existsBoothSlot(
            @Param("hallId") UUID hallId,
            @Param("type") HallHotspotType type,
            @Param("slotIndex") Integer slotIndex,
            @Param("excludedId") UUID excludedId);
}
