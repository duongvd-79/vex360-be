package com.example.vex360.features.hall.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.hall.entities.HallPublishedRevision;

public interface HallPublishedRevisionRepository extends JpaRepository<HallPublishedRevision, UUID> {
    Optional<HallPublishedRevision> findByHallId(UUID hallId);

    Optional<HallPublishedRevision> findByHallExhibitionUuid(UUID exhibitionUuid);
}
