package com.example.vex360.features.exhibition.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.shared.enums.PaymentReceiptStatus;

import jakarta.persistence.LockModeType;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    Optional<PaymentReceipt> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentReceipt r WHERE r.orderCode = :orderCode")
    Optional<PaymentReceipt> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    @Query("SELECT r FROM PaymentReceipt r WHERE r.status IN :statuses AND (r.nextRetryAt IS NULL OR r.nextRetryAt <= :now)")
    List<PaymentReceipt> findClaimableReceipts(@Param("statuses") List<PaymentReceiptStatus> statuses, @Param("now") Instant now, Pageable pageable);
}
