package com.example.vex360.features.exhibition.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.PaymentType;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    interface PaymentRoute {
        PaymentType getPaymentType();

        Integer getRegistrationId();
    }

    @Query("SELECT p.paymentType AS paymentType, r.id AS registrationId " +
            "FROM Payment p LEFT JOIN p.exhibitorRegistration r WHERE p.orderCode = :orderCode")
    Optional<PaymentRoute> findRouteByOrderCode(@Param("orderCode") Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.exhibitorRegistration.id = :registrationId")
    List<Payment> findByExhibitorRegistrationIdForUpdate(@Param("registrationId") Integer registrationId);

    Optional<Payment> findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(Integer exhibitorRegistrationId);

    List<Payment> findByExhibitorRegistrationIdIn(List<Integer> exhibitorRegistrationIds);

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
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);

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
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);
}
