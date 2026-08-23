package com.example.vex360.features.hall.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.hall.entities.HallItem;

import jakarta.persistence.LockModeType;

public interface HallItemRepository extends JpaRepository<HallItem, UUID> {
    List<HallItem> findByHallIdOrderByDisplayOrderAscNameAsc(UUID hallId);

    @Query("""
            SELECT i FROM HallItem i
            JOIN FETCH i.mediaAsset
            WHERE i.hall.id = :hallId
            ORDER BY i.displayOrder ASC, i.name ASC, i.id ASC
            """)
    List<HallItem> findSnapshotItemsByHallId(@Param("hallId") UUID hallId);

    Optional<HallItem> findByIdAndHallId(UUID id, UUID hallId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM HallItem i WHERE i.id = :id AND i.hall.id = :hallId")
    Optional<HallItem> findByIdAndHallIdForUpdate(@Param("id") UUID id, @Param("hallId") UUID hallId);

    boolean existsByMediaAssetId(UUID mediaAssetId);
}
