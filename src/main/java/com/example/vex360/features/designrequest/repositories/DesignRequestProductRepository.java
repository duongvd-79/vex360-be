package com.example.vex360.features.designrequest.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.product.enums.ProductStatus;

public interface DesignRequestProductRepository extends JpaRepository<DesignRequestProduct, UUID> {
    boolean existsByDesignRequestIdAndProductId(UUID requestId, UUID productId);

    @Query(value = """
            SELECT drp FROM DesignRequestProduct drp
            JOIN FETCH drp.product product
            LEFT JOIN FETCH product.category
            WHERE drp.designRequest.id = :requestId
              AND product.status = :status
              AND (:keyword IS NULL OR LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR product.category.id = :categoryId)
            """, countQuery = """
            SELECT COUNT(drp) FROM DesignRequestProduct drp
            WHERE drp.designRequest.id = :requestId
              AND drp.product.status = :status
              AND (:keyword IS NULL OR LOWER(drp.product.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR drp.product.category.id = :categoryId)
            """)
    Page<DesignRequestProduct> searchAllowedProducts(
            @Param("requestId") UUID requestId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);
}
