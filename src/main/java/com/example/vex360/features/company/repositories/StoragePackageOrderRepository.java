package com.example.vex360.features.company.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.company.entities.StoragePackageOrder;

import java.util.List;

public interface StoragePackageOrderRepository extends JpaRepository<StoragePackageOrder, Integer> {
    Optional<StoragePackageOrder> findByOrderCode(Long orderCode);

    List<StoragePackageOrder> findAllByOrderByCreatedAtDesc();
}
