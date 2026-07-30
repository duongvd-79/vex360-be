package com.example.vex360.features.exhibition.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    interface PaymentRoute {
        PaymentType getPaymentType();

        Integer getRegistrationId();
    }

    @Query("SELECT p.paymentType AS paymentType, r.id AS registrationId " +
            "FROM Payment p LEFT JOIN p.exhibitorRegistration r WHERE p.orderCode = :orderCode")
    Optional<PaymentRoute> findRouteByOrderCode(@Param("orderCode") Long orderCode);

    Optional<Payment> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.exhibitorRegistration.id = :registrationId")
    List<Payment> findByExhibitorRegistrationIdForUpdate(@Param("registrationId") Integer registrationId);

    Optional<Payment> findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(Integer exhibitorRegistrationId);

    List<Payment> findByExhibitorRegistrationIdIn(List<Integer> exhibitorRegistrationIds);

    boolean existsByPaymentReferenceAndIdNot(String paymentReference, Integer id);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = com.example.vex360.shared.enums.PaymentStatus.PAID
              AND p.paymentType = com.example.vex360.shared.enums.PaymentType.EXHIBITION_REGISTRATION
              AND p.exhibitorRegistration IS NOT NULL
              AND NOT EXISTS (
                SELECT b.id FROM Booth b WHERE b.exhibitorRegistration.id = p.exhibitorRegistration.id
              )
            """)
    List<Payment> findUnfulfilledPaidPayments(Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = com.example.vex360.shared.enums.PaymentStatus.PENDING
              AND p.paymentType = com.example.vex360.shared.enums.PaymentType.EXHIBITION_REGISTRATION
            """)
    List<Payment> findPendingExhibitionPayments(Pageable pageable);
    /**
     * Doanh thu bán gói đã thanh toán theo từng ngày của một triển lãm — dùng cho
     * biểu đồ doanh thu ở dashboard ban tổ chức.
     * Mỗi dòng = 1 ngày: [ngày, tổng tiền đã thanh toán].
     */
    @Query(value = """
            SELECT DATE(p.paid_at) AS day,
                   COALESCE(SUM(p.amount), 0) AS revenue
            FROM payments p
            JOIN exhibitor_registrations r ON r.id = p.exhibitor_registration_id
            JOIN exhibition_packages ep ON ep.id = r.exhibition_package_id
            WHERE ep.exhibition_id = :exhibitionId
              AND p.status = 'PAID'
              AND p.paid_at BETWEEN :start AND :end
            GROUP BY DATE(p.paid_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> aggregateDailyRevenue(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /** Số payment record PAID (một registration có thể có nhiều record) và doanh thu theo gói. */
    @Query(value = """
            SELECT r.package_name_snapshot AS package_name,
                   COUNT(p.id) AS quantity,
                   COALESCE(SUM(p.amount), 0) AS revenue
            FROM payments p
            JOIN exhibitor_registrations r ON r.id = p.exhibitor_registration_id
            JOIN exhibition_packages ep ON ep.id = r.exhibition_package_id
            WHERE ep.exhibition_id = :exhibitionId
              AND p.status = 'PAID'
              AND p.paid_at BETWEEN :start AND :end
            GROUP BY r.package_name_snapshot
            ORDER BY revenue DESC
            """, nativeQuery = true)
    List<Object[]> aggregatePaidPackageRevenue(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT ep.exhibition_id, COALESCE(SUM(p.amount), 0)
            FROM payments p
            JOIN exhibitor_registrations r ON r.id = p.exhibitor_registration_id
            JOIN exhibition_packages ep ON ep.id = r.exhibition_package_id
            WHERE ep.exhibition_id IN (:exhibitionIds)
              AND p.status = 'PAID'
              AND p.paid_at BETWEEN :start AND :end
            GROUP BY ep.exhibition_id
            """, nativeQuery = true)
    List<Object[]> aggregateRevenueByExhibition(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT DATE(p.paid_at), COALESCE(SUM(p.amount), 0)
            FROM payments p
            JOIN exhibitor_registrations r ON r.id = p.exhibitor_registration_id
            JOIN exhibition_packages ep ON ep.id = r.exhibition_package_id
            WHERE ep.exhibition_id IN (:exhibitionIds)
              AND p.status = 'PAID'
              AND p.paid_at BETWEEN :start AND :end
            GROUP BY DATE(p.paid_at)
            ORDER BY DATE(p.paid_at)
            """, nativeQuery = true)
    List<Object[]> aggregateOrganizerDailyRevenue(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT COALESCE(r.package_name_snapshot, 'Không xác định'),
                   COUNT(p.id),
                   COALESCE(SUM(p.amount), 0)
            FROM payments p
            JOIN exhibitor_registrations r ON r.id = p.exhibitor_registration_id
            JOIN exhibition_packages ep ON ep.id = r.exhibition_package_id
            WHERE ep.exhibition_id IN (:exhibitionIds)
              AND p.status = 'PAID'
              AND p.paid_at BETWEEN :start AND :end
            GROUP BY r.package_name_snapshot
            ORDER BY SUM(p.amount) DESC
            """, nativeQuery = true)
    List<Object[]> aggregateOrganizerPackageRevenue(
            @Param("exhibitionIds") List<Integer> exhibitionIds,
            @Param("start") Instant start,
            @Param("end") Instant end);

    long countByStatus(PaymentStatus status);

    @Query(value = """
            SELECT COUNT(*), COALESCE(SUM(amount), 0), COALESCE(SUM(system_fee), 0)
            FROM payments
            WHERE status = 'PAID'
              AND paid_at BETWEEN :start AND :end
            """, nativeQuery = true)
    List<Object[]> aggregateAdminPaidMetrics(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(value = """
            SELECT DATE(paid_at), COALESCE(SUM(amount), 0)
            FROM payments
            WHERE status = 'PAID'
              AND paid_at BETWEEN :start AND :end
            GROUP BY DATE(paid_at)
            ORDER BY DATE(paid_at)
            """, nativeQuery = true)
    List<Object[]> aggregateAdminDailyRevenue(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT p.status, COUNT(p)
            FROM Payment p
            WHERE p.createdAt BETWEEN :start AND :end
            GROUP BY p.status
            """)
    List<Object[]> countAdminPaymentsByStatus(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
