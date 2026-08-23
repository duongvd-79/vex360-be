package com.example.vex360.features.hall.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.hall.entities.HallPanorama;

import jakarta.persistence.LockModeType;

public interface HallPanoramaRepository extends JpaRepository<HallPanorama, UUID> {
    List<HallPanorama> findByHallIdOrderByOrderIndexAsc(UUID hallId);

    Optional<HallPanorama> findByIdAndHallId(UUID id, UUID hallId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM HallPanorama p WHERE p.id = :id AND p.hall.id = :hallId")
    Optional<HallPanorama> findByIdAndHallIdForUpdate(@Param("id") UUID id, @Param("hallId") UUID hallId);

    @Query("SELECT COALESCE(MAX(p.orderIndex), -1) FROM HallPanorama p WHERE p.hall.id = :hallId")
    int findMaxOrderIndexByHallId(@Param("hallId") UUID hallId);

    @Modifying
    @Query("UPDATE HallPanorama p SET p.isDefault = false WHERE p.hall.id = :hallId")
    void clearDefaultForHall(@Param("hallId") UUID hallId);
}
