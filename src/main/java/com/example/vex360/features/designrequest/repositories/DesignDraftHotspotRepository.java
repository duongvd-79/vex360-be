package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;

public interface DesignDraftHotspotRepository extends JpaRepository<DesignDraftHotspot, UUID> {
    Optional<DesignDraftHotspot> findByIdAndSourcePanoramaId(UUID id, UUID sourcePanoramaId);

    List<DesignDraftHotspot> findBySourcePanoramaDraftIdAndTargetDraftPanoramaKey(
            UUID draftId,
            String targetDraftPanoramaKey);
}
