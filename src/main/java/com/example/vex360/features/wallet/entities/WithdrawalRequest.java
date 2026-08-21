package com.example.vex360.features.wallet.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.enums.WithdrawalStatus;

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
@Table(name = "withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    @Builder.Default
    UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    OrganizerWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    Company company;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    String currency = "VND";

    @Column(name = "minimum_amount_snapshot", nullable = false, precision = 15, scale = 2)
    BigDecimal minimumAmountSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "bank_code_snapshot", nullable = false, length = 50)
    String bankCodeSnapshot;

    @Column(name = "bank_name_snapshot", nullable = false, length = 100)
    String bankNameSnapshot;

    @Column(name = "account_number_snapshot", nullable = false, length = 50)
    String accountNumberSnapshot;

    @Column(name = "account_holder_name_snapshot", nullable = false, length = 255)
    String accountHolderNameSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    User requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    User approvedBy;

    @Column(name = "approved_at")
    Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by")
    User paidBy;

    @Column(name = "paid_at")
    Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    User rejectedBy;

    @Column(name = "rejected_at")
    Instant rejectedAt;

    @Column(name = "rejected_reason", length = 500)
    String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    User canceledBy;

    @Column(name = "canceled_at")
    Instant canceledAt;

    @Column(name = "transfer_reference", unique = true, length = 100)
    String transferReference;

    @Column(name = "proof_url", length = 1000)
    String proofUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
