package com.example.vex360.features.company.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;

public interface StoragePackageOrderRepository extends JpaRepository<StoragePackageOrder, Integer> {
    Optional<StoragePackageOrder> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM StoragePackageOrder o WHERE o.id = :id")
    Optional<StoragePackageOrder> findByIdForUpdate(@Param("id") Integer id);

    @Query(value = """
            SELECT o FROM StoragePackageOrder o
            JOIN FETCH o.company c
            JOIN FETCH o.storagePackage p
            WHERE (:keyword IS NULL
                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR CAST(o.orderCode AS string) LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR o.status = :status)
            """, countQuery = """
            SELECT COUNT(o) FROM StoragePackageOrder o
            JOIN o.company c
            WHERE (:keyword IS NULL
                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR CAST(o.orderCode AS string) LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<StoragePackageOrder> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") StoragePackageOrderStatus status,
            Pageable pageable);
}
