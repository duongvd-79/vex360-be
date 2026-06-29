package com.example.vex360.features.product.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByCompanyIdAndStatusNotOrderByCreatedAtDesc(UUID companyId, ProductStatus status, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.company.id = :companyId
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND ((:status IS NULL AND p.status <> com.example.vex360.features.product.enums.ProductStatus.ARCHIVED)
                   OR (:status IS NOT NULL AND p.status = :status))
            """)
    Page<Product> searchProducts(
            @Param("companyId") UUID companyId,
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("status") ProductStatus status,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.status = :status
            WHERE p.category.id = :categoryId
              AND p.company.id = :companyId
            """)
    int updateStatusByCategoryIdAndCompanyId(
            @Param("categoryId") UUID categoryId,
            @Param("companyId") UUID companyId,
            @Param("status") ProductStatus status);

    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndSkuIgnoreCase(UUID companyId, String sku);

    boolean existsByCompanyIdAndSkuIgnoreCaseAndIdNot(UUID companyId, String sku, UUID id);
}
