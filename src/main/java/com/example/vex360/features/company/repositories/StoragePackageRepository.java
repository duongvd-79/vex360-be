package com.example.vex360.features.company.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.company.entities.StoragePackage;

public interface StoragePackageRepository extends JpaRepository<StoragePackage, Integer> {
    List<StoragePackage> findByIsActiveTrueOrderByPriceVndAsc();

    @Query("""
            SELECT p FROM StoragePackage p
            WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:isActive IS NULL OR p.isActive = :isActive)
            """)
    Page<StoragePackage> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    boolean existsByNameIgnoreCase(String name);
}
