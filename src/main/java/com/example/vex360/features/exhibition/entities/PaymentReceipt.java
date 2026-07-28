package com.example.vex360.features.exhibition.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.shared.enums.PaymentReceiptStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "payment_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_receipts_order_code", columnNames = "order_code")
}, indexes = {
        @Index(name = "idx_receipts_status_next_retry", columnList = "status, next_retry_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    Long orderCode;

    @Column(name = "payment_reference")
    String paymentReference;

    @Column(name = "registration_id")
    Integer registrationId;

    @Column(name = "booth_id")
    UUID boothId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    PaymentReceiptStatus status = PaymentReceiptStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    @Column(name = "next_retry_at")
    Instant nextRetryAt;

    @Column(name = "payload", columnDefinition = "TEXT")
    String payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
