package com.example.vex360.features.exhibition.entities;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "exhibitor_registration_id", nullable = true)
    ExhibitorRegistration exhibitorRegistration;

    @Column(name = "storage_package_order_id")
    Integer storagePackageOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    PaymentType paymentType = PaymentType.EXHIBITION_REGISTRATION;

    @Column(name = "order_code", nullable = false, unique = true)
    Long orderCode;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    BigDecimal amount;

    @Column(name = "system_fee", nullable = false, precision = 15, scale = 2)
    BigDecimal systemFee;

    @Column(name = "organizer_payout", nullable = false, precision = 15, scale = 2)
    BigDecimal organizerPayout;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    String currency = "VND";

    @Column(name = "payment_provider", nullable = false, length = 50)
    @Builder.Default
    String paymentProvider = "PAYOS";

    @Column(name = "payment_reference")
    String paymentReference;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    PaymentStatus status;

    @Column(name = "paid_at")
    Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
