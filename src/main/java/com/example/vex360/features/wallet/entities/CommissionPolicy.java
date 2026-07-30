package com.example.vex360.features.wallet.entities;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.features.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "commission_policies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commission_policies_effective_at", columnNames = "effective_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommissionPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(name = "rate_basis_points", nullable = false)
    Integer rateBasisPoints;

    @Column(name = "effective_at", nullable = false)
    Instant effectiveAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
