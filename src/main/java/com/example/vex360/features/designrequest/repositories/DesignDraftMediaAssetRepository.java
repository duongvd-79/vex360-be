package com.example.vex360.features.designrequest.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;

public interface DesignDraftMediaAssetRepository extends JpaRepository<DesignDraftMediaAsset, UUID> {
}
