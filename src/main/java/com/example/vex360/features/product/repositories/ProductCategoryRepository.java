package com.example.vex360.features.product.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.shared.entities.ProductCategory;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<ProductCategory> findByCompanyIdAndStatusOrderByNameAsc(UUID companyId, ProductCategoryStatus status);

    Optional<ProductCategory> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);
}
