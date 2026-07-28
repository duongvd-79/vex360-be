package com.example.vex360.features.lead.repositories;

import com.example.vex360.features.lead.entities.BoothLead;
import com.example.vex360.features.lead.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoothLeadRepository extends JpaRepository<BoothLead, UUID> {

    Optional<BoothLead> findByBoothIdAndVisitorId(UUID boothId, UUID visitorId);

    long deleteByEmailIgnoreCaseAndVisitorIsNull(String email);

    @Query("""
            SELECT lead
            FROM BoothLead lead
            JOIN lead.booth booth
            WHERE booth.company.id = :companyId
              AND (:boothId IS NULL OR booth.id = :boothId)
              AND (:status IS NULL OR lead.status = :status)
              AND (:keyword IS NULL
                OR LOWER(lead.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(lead.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(booth.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<BoothLead> searchForCompany(
            @Param("companyId") UUID companyId,
            @Param("boothId") UUID boothId,
            @Param("status") LeadStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT lead
            FROM BoothLead lead
            JOIN lead.booth booth
            WHERE lead.id = :leadId
              AND booth.company.id = :companyId
            """)
    Optional<BoothLead> findOwnedLead(
            @Param("leadId") UUID leadId,
            @Param("companyId") UUID companyId);

    @Query("""
            SELECT lead.status, COUNT(lead)
            FROM BoothLead lead
            JOIN lead.booth booth
            WHERE booth.company.id = :companyId
              AND (:boothId IS NULL OR booth.id = :boothId)
            GROUP BY lead.status
            """)
    List<Object[]> countByStatusForCompany(
            @Param("companyId") UUID companyId,
            @Param("boothId") UUID boothId);

    @Query("""
            SELECT lead.status, COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.id = :boothId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY lead.status
            """)
    List<Object[]> countByStatusForBoothInRange(
            @Param("boothId") UUID boothId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT lead.status, COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.company.id = :companyId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY lead.status
            """)
    List<Object[]> countByStatusForCompanyInRange(
            @Param("companyId") UUID companyId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT lead.status, COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY lead.status
            """)
    List<Object[]> countByStatusForExhibitionInRange(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT FUNCTION('DATE', lead.createdAt), COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY FUNCTION('DATE', lead.createdAt)
            ORDER BY FUNCTION('DATE', lead.createdAt)
            """)
    List<Object[]> aggregateDailyForExhibition(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT lead.booth.id, lead.booth.name, COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY lead.booth.id, lead.booth.name
            ORDER BY COUNT(lead) DESC, lead.booth.name ASC
            """)
    List<Object[]> findTopBoothsForExhibition(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime,
            Pageable pageable);

    @Query("""
            SELECT COUNT(DISTINCT lead.booth.id)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            """)
    long countBoothsWithLeadsForExhibition(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT COUNT(DISTINCT lead.visitor.id)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id = :exhibitionId
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            """)
    long countUniqueLeadVisitorsForExhibition(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT exhibition.id,
                   COUNT(lead),
                   COUNT(DISTINCT lead.visitor.id),
                   COUNT(DISTINCT lead.booth.id)
            FROM BoothLead lead
            JOIN lead.booth booth
            JOIN booth.exhibitorRegistration registration
            JOIN registration.exhibitionPackage exhibitionPackage
            JOIN exhibitionPackage.exhibition exhibition
            WHERE exhibition.id IN :exhibitionIds
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY exhibition.id
            """)
    List<Object[]> aggregatePerformanceForExhibitions(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT FUNCTION('DATE', lead.createdAt), COUNT(lead)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id IN :exhibitionIds
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY FUNCTION('DATE', lead.createdAt)
            ORDER BY FUNCTION('DATE', lead.createdAt)
            """)
    List<Object[]> aggregateDailyForExhibitions(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT COUNT(DISTINCT lead.visitor.id)
            FROM BoothLead lead
            WHERE lead.booth.exhibitorRegistration.exhibitionPackage.exhibition.id IN :exhibitionIds
              AND lead.createdAt BETWEEN :startDateTime AND :endDateTime
            """)
    long countUniqueLeadVisitorsForExhibitions(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);

    @Query("""
            SELECT lead.status, COUNT(lead)
            FROM BoothLead lead
            WHERE lead.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY lead.status
            """)
    List<Object[]> countAdminLeadsByStatus(
            @Param("startDateTime") Instant startDateTime,
            @Param("endDateTime") Instant endDateTime);
}
