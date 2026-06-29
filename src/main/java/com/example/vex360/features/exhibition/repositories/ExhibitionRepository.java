package com.example.vex360.features.exhibition.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.enums.ExhibitionStatus;

@Repository
public interface ExhibitionRepository extends JpaRepository<Exhibition, Integer> {
    Optional<Exhibition> findByUuid(UUID uuid);

    @Query(value = """
            SELECT e FROM Exhibition e
            LEFT JOIN FETCH e.organizer o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(o.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(o.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """, countQuery = """
            SELECT COUNT(e) FROM Exhibition e
            LEFT JOIN e.organizer o
            WHERE (:keyword IS NULL
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(o.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(o.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """)
    Page<Exhibition> searchExhibitions(
            @Param("keyword") String keyword,
            @Param("status") ExhibitionStatus status,
            @Param("category") String category,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            Pageable pageable);

    @Query("SELECT e.status, COUNT(e) FROM Exhibition e GROUP BY e.status")
    List<Object[]> countExhibitionsByStatus();

    long countByOrganizerIdAndStatus(UUID organizerId, ExhibitionStatus status);

    boolean existsByName(String name);

    @Query(value = """
            SELECT e FROM Exhibition e
            LEFT JOIN FETCH e.organizer o
            WHERE o.id = :organizerId
              AND (:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
              AND (:startDate IS NULL OR e.startDate >= :startDate)
              AND (:endDate IS NULL OR e.endDate <= :endDate)
            """,
           countQuery = """
            SELECT COUNT(e) FROM Exhibition e
            LEFT JOIN e.organizer o
            WHERE o.id = :organizerId
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
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            Pageable pageable);
}
