package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;

public interface DesignDraftMediaAssetRepository extends JpaRepository<DesignDraftMediaAsset, UUID> {
    List<DesignDraftMediaAsset> findByDraftIdOrderBySortOrderAsc(UUID draftId);

    Optional<DesignDraftMediaAsset> findByIdAndDraftId(UUID id, UUID draftId);

    void deleteByDraftIdAndId(UUID draftId, UUID id);
}
