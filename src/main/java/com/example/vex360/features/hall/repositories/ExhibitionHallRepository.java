package com.example.vex360.features.hall.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.hall.entities.ExhibitionHall;

import jakarta.persistence.LockModeType;

public interface ExhibitionHallRepository extends JpaRepository<ExhibitionHall, UUID> {
    Optional<ExhibitionHall> findByExhibitionId(Integer exhibitionId);

    boolean existsByExhibitionId(Integer exhibitionId);

    boolean existsByBackgroundMusicPublicId(String backgroundMusicPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ExhibitionHall h WHERE h.exhibition.id = :exhibitionId")
    Optional<ExhibitionHall> findByExhibitionIdForUpdate(@Param("exhibitionId") Integer exhibitionId);
}
