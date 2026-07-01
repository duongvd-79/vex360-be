package com.example.vex360.features.booth.repositories;

import java.util.UUID;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Panorama;

@Repository
public interface PanoramaRepository extends JpaRepository<Panorama, UUID> {
    List<Panorama> findByBoothIdOrderByOrderIndexAsc(UUID boothId);

    Optional<Panorama> findByIdAndBoothId(UUID id, UUID boothId);

    @Modifying
    @Query("""
            UPDATE Panorama p
            SET p.isDefault = false
            WHERE p.booth.id = :boothId
            """)
    void clearDefaultForBooth(@Param("boothId") UUID boothId);
}
