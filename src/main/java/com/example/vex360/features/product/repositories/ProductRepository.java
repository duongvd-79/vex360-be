package com.example.vex360.features.product.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.entities.Product;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.company.id = :companyId")
    Optional<Product> findByIdAndCompanyIdForUpdate(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    boolean existsByThumbnailPublicId(String publicId);

    @Query("SELECT COUNT(content) > 0 FROM ProductContent content WHERE content.publicId = :publicId")
    boolean existsContentByPublicId(@Param("publicId") String publicId);

    @Query("""
            SELECT p FROM Product p
            WHERE p.company.id = :companyId
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:status IS NULL OR p.status = :status)
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

    @Query("""
            SELECT DISTINCT product
            FROM Product product
            JOIN FETCH product.company
            JOIN FETCH product.category
            LEFT JOIN FETCH product.contents
            WHERE product.id IN :productIds
              AND product.status = :status
            """)
    List<Product> findAllDetailsByIdInAndStatus(
            @Param("productIds") Collection<UUID> productIds,
            @Param("status") ProductStatus status);

    List<Product> findByIdInAndCompanyId(List<UUID> ids, UUID companyId);

    boolean existsByCompanyIdAndSkuIgnoreCase(UUID companyId, String sku);

    boolean existsByCompanyIdAndSkuIgnoreCaseAndIdNot(UUID companyId, String sku, UUID id);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM hotspots h
                JOIN panoramas p ON p.id = h.source_panorama_id
                JOIN booths b ON b.id = p.booth_id
                WHERE h.product_id = :productId
                  AND b.status IN (:boothStatuses)
            )
            """, nativeQuery = true)
    Long existsInBoothWithStatus(
            @Param("productId") UUID productId,
            @Param("boothStatuses") Collection<String> boothStatuses);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM products product
                JOIN hotspots h ON h.product_id = product.id
                JOIN panoramas p ON p.id = h.source_panorama_id
                JOIN booths b ON b.id = p.booth_id
                WHERE product.category_id = :categoryId
                  AND product.company_id = :companyId
                  AND b.status IN (:boothStatuses)
            )
            """, nativeQuery = true)
    Long existsCategoryProductInBoothWithStatus(
            @Param("categoryId") UUID categoryId,
            @Param("companyId") UUID companyId,
            @Param("boothStatuses") Collection<String> boothStatuses);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM design_request_products drp
                JOIN design_requests dr ON dr.id = drp.design_request_id
                WHERE drp.product_id = :productId
                  AND dr.status IN ('PENDING', 'ASSIGNED', 'DRAFT_SUBMITTED',
                                    'REVISION_REQUESTED')
            )
            """, nativeQuery = true)
    Long existsLockedByDesignRequest(@Param("productId") UUID productId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM design_request_products drp
                JOIN design_requests dr ON dr.id = drp.design_request_id
                JOIN products product ON product.id = drp.product_id
                WHERE product.category_id = :categoryId
                  AND product.company_id = :companyId
                  AND dr.status IN ('PENDING', 'ASSIGNED', 'DRAFT_SUBMITTED',
                                    'REVISION_REQUESTED')
            )
            """, nativeQuery = true)
    Long existsCategoryLockedByDesignRequest(
            @Param("categoryId") UUID categoryId,
            @Param("companyId") UUID companyId);
}
