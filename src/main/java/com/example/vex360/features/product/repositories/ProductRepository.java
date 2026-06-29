package com.example.vex360.features.product.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByCompanyIdAndStatusNotOrderByCreatedAtDesc(UUID companyId, ProductStatus status, Pageable pageable);

    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndSkuIgnoreCase(UUID companyId, String sku);

    boolean existsByCompanyIdAndSkuIgnoreCaseAndIdNot(UUID companyId, String sku, UUID id);
}
