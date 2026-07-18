package com.example.vex360.features.company.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.company.entities.StoragePackage;

public interface StoragePackageRepository extends JpaRepository<StoragePackage, Integer> {
    List<StoragePackage> findByIsActiveTrueOrderByPriceVndAsc();
    List<StoragePackage> findAllByOrderByPriceVndAsc();
    boolean existsByNameIgnoreCase(String name);
}
