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
}
