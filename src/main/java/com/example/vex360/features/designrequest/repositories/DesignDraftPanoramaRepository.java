package com.example.vex360.features.designrequest.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;

public interface DesignDraftPanoramaRepository extends JpaRepository<DesignDraftPanorama, UUID> {
    boolean existsByImageKey(String imageKey);
}
