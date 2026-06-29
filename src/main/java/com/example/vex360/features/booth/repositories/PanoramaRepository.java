package com.example.vex360.features.booth.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Panorama;

@Repository
public interface PanoramaRepository extends JpaRepository<Panorama, UUID> {
}
