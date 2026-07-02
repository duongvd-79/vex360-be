package com.example.vex360.features.booth.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;

@Repository
public interface BoothRepository extends JpaRepository<Booth, UUID> {
    @Query("""
            SELECT b FROM Booth b
            WHERE b.isTemplate = true
              AND (:keyword IS NULL
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<Booth> searchTemplates(
            @Param("keyword") String keyword,
            @Param("status") BoothStatus status,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id AND b.isTemplate = true
            """)
    Optional<Booth> findTemplateById(@Param("id") UUID id);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.isTemplate = false
              AND b.company.id = :companyId
            """)
    Page<Booth> findCompanyBooths(
            @Param("companyId") UUID companyId,
            Pageable pageable);

    @Query("""
            SELECT b FROM Booth b
            WHERE b.id = :id
              AND b.isTemplate = false
              AND b.company.id = :companyId
            """)
    Optional<Booth> findCompanyBoothById(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId);

    Optional<Booth> findByExhibitorRegistrationId(Integer exhibitorRegistrationId);
}
