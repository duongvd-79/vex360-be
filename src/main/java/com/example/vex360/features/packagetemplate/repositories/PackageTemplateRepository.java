package com.example.vex360.features.packagetemplate.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.enums.PackageTemplateStatus;

@Repository
public interface PackageTemplateRepository extends JpaRepository<PackageTemplate, UUID> {
    @Query("""
            SELECT p FROM PackageTemplate p
            WHERE (:keyword IS NULL
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<PackageTemplate> searchPackageTemplates(
            @Param("keyword") String keyword,
            @Param("status") PackageTemplateStatus status,
            Pageable pageable);

    List<PackageTemplate> findByStatus(PackageTemplateStatus status, Sort sort);

    Optional<PackageTemplate> findByIdAndStatus(UUID id, PackageTemplateStatus status);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
