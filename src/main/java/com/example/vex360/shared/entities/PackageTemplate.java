package com.example.vex360.shared.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "package_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_package_templates_name", columnNames = { "name" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PackageTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    String description;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    BigDecimal price;

    @Column(name = "currency", nullable = false, length = 10)
    String currency;

    @Column(name = "max_products_per_booth", nullable = false)
    Integer maxProductsPerBooth;

    @Column(name = "max_embedded_videos_per_booth", nullable = false)
    Integer maxEmbeddedVideosPerBooth;

    @Column(name = "max_panoramas_per_booth", nullable = false)
    Integer maxPanoramasPerBooth;

    @Column(name = "max_hotspots_per_booth", nullable = false)
    Integer maxHotspotsPerBooth;

    @Column(name = "storage_limit_mb", nullable = false)
    Long storageLimitMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_priority", nullable = false)
    BoothListingPriority listingPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    PackageTemplateStatus status = PackageTemplateStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
