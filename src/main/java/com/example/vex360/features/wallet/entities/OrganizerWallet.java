package com.example.vex360.features.wallet.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.company.entities.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "organizer_wallets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_organizer_wallets_company", columnNames = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrganizerWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    Company company;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    String currency = "VND";

    @Column(name = "pending_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(name = "withdrawn_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    BigDecimal withdrawnTotal = BigDecimal.ZERO;

    @Version
    @Column(name = "version")
    Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
