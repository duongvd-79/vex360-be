package com.example.vex360.shared.entities;

import java.util.UUID;

import com.example.vex360.shared.enums.CompanyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "companies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_companies_owner_user", columnNames = "owner_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User ownerUser;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "industry")
    String industry;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "logo_url", length = 1000)
    String logoUrl;

    @Column(name = "website")
    String website;

    @Column(name = "email")
    String email;

    @Column(name = "phone")
    String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    CompanyStatus status = CompanyStatus.INCOMPLETE_PROFILE;
}
