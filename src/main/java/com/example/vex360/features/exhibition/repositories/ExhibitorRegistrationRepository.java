package com.example.vex360.features.exhibition.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExhibitorRegistrationRepository extends JpaRepository<ExhibitorRegistration, Integer> {
    Optional<ExhibitorRegistration> findByUuid(UUID uuid);

    boolean existsByExhibitionPackageExhibitionId(Integer exhibitionId);

    boolean existsByExhibitionPackageId(Integer exhibitionPackageId);

    @Query("SELECT COUNT(r) > 0 FROM ExhibitorRegistration r " +
            "WHERE r.company.id = :companyId " +
            "AND r.exhibitionPackage.exhibition.id = :exhibitionId " +
            "AND r.status IN :statuses")
    boolean existsActiveRegistration(
            @Param("companyId") UUID companyId,
            @Param("exhibitionId") Integer exhibitionId,
            @Param("statuses") Collection<ExhibitorRegistrationStatus> statuses);

    @Query(value = "SELECT r FROM ExhibitorRegistration r " +
            "LEFT JOIN FETCH r.company " +
            "LEFT JOIN FETCH r.exhibitionPackage p " +
            "LEFT JOIN FETCH p.template " +
            "LEFT JOIN FETCH p.exhibition e " +
            "LEFT JOIN FETCH r.reviewedBy " +
            "WHERE e.organizer.id = :organizerId " +
            "AND (:exhibitionUuid IS NULL OR e.uuid = :exhibitionUuid) " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(r.company.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(r) FROM ExhibitorRegistration r "
                    +
                    "WHERE r.exhibitionPackage.exhibition.organizer.id = :organizerId " +
                    "AND (:exhibitionUuid IS NULL OR r.exhibitionPackage.exhibition.uuid = :exhibitionUuid) " +
                    "AND (:status IS NULL OR r.status = :status) " +
                    "AND (:keyword IS NULL OR LOWER(r.company.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.exhibitionPackage.exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExhibitorRegistration> searchForOrganizer(
            @Param("organizerId") UUID organizerId,
            @Param("exhibitionUuid") UUID exhibitionUuid,
            @Param("status") ExhibitorRegistrationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = "SELECT r FROM ExhibitorRegistration r " +
            "LEFT JOIN FETCH r.company " +
            "LEFT JOIN FETCH r.exhibitionPackage p " +
            "LEFT JOIN FETCH p.template " +
            "LEFT JOIN FETCH p.exhibition e " +
            "LEFT JOIN FETCH r.reviewedBy " +
            "WHERE r.company.id = :companyId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(r) FROM ExhibitorRegistration r "
                    +
                    "WHERE r.company.id = :companyId " +
                    "AND (:status IS NULL OR r.status = :status) " +
                    "AND (:keyword IS NULL OR LOWER(r.exhibitionPackage.exhibition.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExhibitorRegistration> searchForExhibitor(
            @Param("companyId") UUID companyId,
            @Param("status") ExhibitorRegistrationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
