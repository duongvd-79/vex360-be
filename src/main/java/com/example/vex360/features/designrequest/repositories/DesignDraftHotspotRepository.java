package com.example.vex360.features.designrequest.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;

public interface DesignDraftHotspotRepository extends JpaRepository<DesignDraftHotspot, UUID> {
}
