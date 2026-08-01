package com.example.vex360.features.exhibition.entities;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

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
@Table(name = "exhibitor_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitorRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(name = "uuid", nullable = false, unique = true)
    @Builder.Default
    UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_package_id", nullable = false)
    ExhibitionPackage exhibitionPackage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    ExhibitorRegistrationStatus status;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    User reviewedBy;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    String rejectedReason;

    @Column(name = "participation_reason", columnDefinition = "TEXT")
    String participationReason;

    @Column(name = "booth_name", nullable = false, length = 255)
    String boothName;

    @Column(name = "booth_description", nullable = false, columnDefinition = "TEXT")
    String boothDescription;

    // Snapshot fields to preserve template state at registration time
    @Column(name = "package_name_snapshot")
    String packageNameSnapshot;

    @Column(name = "price_snapshot", precision = 15, scale = 2)
    BigDecimal priceSnapshot;

    @Column(name = "final_price_snapshot", precision = 15, scale = 2)
    BigDecimal finalPriceSnapshot;

    @Column(name = "currency_snapshot", length = 10)
    String currencySnapshot;

    @Column(name = "max_products_per_booth_snapshot")
    Integer maxProductsPerBoothSnapshot;

    @Column(name = "max_embedded_videos_per_booth_snapshot")
    Integer maxEmbeddedVideosPerBoothSnapshot;

    @Column(name = "max_panoramas_per_booth_snapshot")
    Integer maxPanoramasPerBoothSnapshot;

    @Column(name = "max_hotspots_per_booth_snapshot")
    Integer maxHotspotsPerBoothSnapshot;

    @Column(name = "storage_limit_mb_snapshot")
    Long storageLimitMbSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_priority_snapshot", columnDefinition = "VARCHAR(50)")
    BoothListingPriority listingPrioritySnapshot;
}
