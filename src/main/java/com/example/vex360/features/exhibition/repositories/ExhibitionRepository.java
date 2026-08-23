package com.example.vex360.features.exhibition.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.ExhibitionStatus;

import jakarta.persistence.LockModeType;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Integer> {
    Optional<Exhibition> findByUuid(UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Exhibition e WHERE e.id = :id")
    Optional<Exhibition> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Exhibition e WHERE e.uuid = :uuid")
    Optional<Exhibition> findByUuidForUpdate(@Param("uuid") UUID uuid);

    @Query(value = """
            SELECT e FROM Exhibition e
            LEFT JOIN e.organizer o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT c.id FROM Company c
                    WHERE c.ownerUser = o
                      AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
              AND (e.status IN :statuses)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """, countQuery = """
            SELECT COUNT(e) FROM Exhibition e
            LEFT JOIN e.organizer o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT c.id FROM Company c
                    WHERE c.ownerUser = o
                      AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
              AND (e.status IN :statuses)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """)
    Page<Exhibition> searchExhibitions(
            @Param("keyword") String keyword,
            @Param("statuses") List<ExhibitionStatus> statuses,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = """
            SELECT e AS exhibition, COALESCE(c.name, o.fullName) AS companyName
            FROM Exhibition e
            LEFT JOIN e.organizer o
            LEFT JOIN Company c ON c.ownerUser = o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (e.status IN :statuses)
              AND (:experienceMode IS NULL OR e.experienceMode = :experienceMode)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """, countQuery = """
            SELECT COUNT(e) FROM Exhibition e
            LEFT JOIN e.organizer o
            LEFT JOIN Company c ON c.ownerUser = o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (e.status IN :statuses)
              AND (:experienceMode IS NULL OR e.experienceMode = :experienceMode)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """)
    Page<AdminExhibitionProjection> searchAdminExhibitions(
            @Param("keyword") String keyword,
            @Param("statuses") List<ExhibitionStatus> statuses,
            @Param("experienceMode") ExhibitionExperienceMode experienceMode,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    long countByStatus(ExhibitionStatus status);

    @Query("""
            SELECT FUNCTION('DATE', e.createdAt), COUNT(e)
            FROM Exhibition e
            WHERE e.createdAt BETWEEN :start AND :end
            GROUP BY FUNCTION('DATE', e.createdAt)
            ORDER BY FUNCTION('DATE', e.createdAt)
            """)
    List<Object[]> aggregateDailyCreated(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT e.status, COUNT(e) FROM Exhibition e GROUP BY e.status")
    List<Object[]> countExhibitionsByStatus();

    long countByOrganizerIdAndStatus(UUID organizerId, ExhibitionStatus status);

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query(value = """
            SELECT DISTINCT e FROM Exhibition e
            LEFT JOIN FETCH e.organizer o
            LEFT JOIN FETCH e.reviewedBy r
            WHERE o.id = :organizerId
              AND (:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """, countQuery = """
            SELECT COUNT(e) FROM Exhibition e
            WHERE e.organizer.id = :organizerId
              AND (:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """)
    Page<Exhibition> searchOrganizerExhibitions(
            @Param("organizerId") UUID organizerId,
            @Param("keyword") String keyword,
            @Param("status") ExhibitionStatus status,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    List<Exhibition> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    Page<Exhibition> findByOrganizerId(UUID organizerId, Pageable pageable);

    @Query("""
            SELECT e.id FROM Exhibition e
            WHERE (e.status = com.example.vex360.shared.enums.ExhibitionStatus.REGISTRATION AND e.startDate <= :registrationDeadline)
               OR (e.status = com.example.vex360.shared.enums.ExhibitionStatus.PUBLISHED AND e.startDate <= :today)
               OR (e.status = com.example.vex360.shared.enums.ExhibitionStatus.ACTIVE AND e.endDate < :today)
            """)
    List<Integer> findDueForLifecycleTransition(@Param("today") LocalDate today,
            @Param("registrationDeadline") LocalDate registrationDeadline);
}
