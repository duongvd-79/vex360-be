package com.example.vex360.features.wallet.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.enums.WalletBucket;
import com.example.vex360.features.wallet.enums.WalletTransactionType;

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
@Table(name = "organizer_wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrganizerWalletTransaction {
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id")
    Exhibition exhibition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_request_id")
    WithdrawalRequest withdrawalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(50)")
    WalletTransactionType type;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_bucket", nullable = false, columnDefinition = "VARCHAR(30)")
    WalletBucket fromBucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_bucket", nullable = false, columnDefinition = "VARCHAR(30)")
    WalletBucket toBucket;

    @Column(name = "pending_after", nullable = false, precision = 15, scale = 2)
    BigDecimal pendingAfter;

    @Column(name = "available_after", nullable = false, precision = 15, scale = 2)
    BigDecimal availableAfter;

    @Column(name = "reserved_after", nullable = false, precision = 15, scale = 2)
    BigDecimal reservedAfter;

    @Column(name = "withdrawn_after", nullable = false, precision = 15, scale = 2)
    BigDecimal withdrawnAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    User actorUser;

    @Column(name = "reason", length = 500)
    String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
