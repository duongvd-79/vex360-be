package com.example.vex360.features.wallet.entities;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "company_payout_profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_company_payout_profiles_company", columnNames = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompanyPayoutProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    Company company;

    @Column(name = "bank_code", nullable = false, length = 50)
    String bankCode;

    @Column(name = "bank_name_snapshot", nullable = false, length = 100)
    String bankNameSnapshot;

    @Column(name = "account_number_ciphertext", nullable = false, columnDefinition = "TEXT")
    String accountNumberCiphertext;

    @Column(name = "account_number_nonce", nullable = false, length = 255)
    String accountNumberNonce;

    @Column(name = "encryption_key_version", nullable = false)
    Integer encryptionKeyVersion;

    @Column(name = "account_number_last4", nullable = false, length = 10)
    String accountNumberLast4;

    @Column(name = "account_holder_name", nullable = false, length = 255)
    String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    PayoutProfileStatus status = PayoutProfileStatus.PENDING_VERIFICATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    User verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    User rejectedBy;

    @Column(name = "rejected_at")
    Instant rejectedAt;

    @Column(name = "rejected_reason", length = 500)
    String rejectedReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
