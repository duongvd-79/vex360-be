package com.example.vex360.features.booth.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Hotspot;

@Repository
public interface HotspotRepository extends JpaRepository<Hotspot, UUID> {
}
