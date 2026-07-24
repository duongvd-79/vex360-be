package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;

public interface DesignDraftPanoramaRepository extends JpaRepository<DesignDraftPanorama, UUID> {
    Optional<DesignDraftPanorama> findByIdAndDraftId(UUID id, UUID draftId);

    List<DesignDraftPanorama> findByDraftIdOrderByOrderIndexAsc(UUID draftId);

    boolean existsByImageKey(String imageKey);
}
