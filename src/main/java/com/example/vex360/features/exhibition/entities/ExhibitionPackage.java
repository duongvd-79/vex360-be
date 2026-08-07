package com.example.vex360.features.exhibition.entities;

import com.example.vex360.features.packagetemplate.entities.PackageTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;

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
@Table(name = "exhibition_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitionPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    PackageTemplate template;

    @Column(name = "package_name_snapshot", nullable = false)
    String packageNameSnapshot;

    @Column(name = "package_description_snapshot", nullable = false, columnDefinition = "TEXT")
    String packageDescriptionSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 15, scale = 2)
    BigDecimal priceSnapshot;

    @Column(name = "currency_snapshot", nullable = false, length = 10)
    String currencySnapshot;

    @Column(name = "max_products_per_booth_snapshot", nullable = false)
    Integer maxProductsPerBoothSnapshot;

    @Column(name = "max_embedded_videos_per_booth_snapshot", nullable = false)
    Integer maxEmbeddedVideosPerBoothSnapshot;

    @Column(name = "max_panoramas_per_booth_snapshot", nullable = false)
    Integer maxPanoramasPerBoothSnapshot;

    @Column(name = "max_hotspots_per_booth_snapshot", nullable = false)
    Integer maxHotspotsPerBoothSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_priority_snapshot", nullable = false, columnDefinition = "VARCHAR(50)")
    BoothListingPriority listingPrioritySnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false)
    Exhibition exhibition;

    @Column(name = "final_price", nullable = false, precision = 15, scale = 2)
    BigDecimal finalPrice;

    @Column(name = "max_booths")
    Integer maxBooths;

    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(50)")
    @Enumerated(EnumType.STRING)
    ExhibitionPackageStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    public void snapshotTemplateTerms(PackageTemplate template) {
        this.template = template;
        this.packageNameSnapshot = template.getName();
        this.packageDescriptionSnapshot = template.getDescription();
        this.priceSnapshot = template.getPrice();
        this.currencySnapshot = template.getCurrency();
        this.maxProductsPerBoothSnapshot = template.getMaxProductsPerBooth();
        this.maxEmbeddedVideosPerBoothSnapshot = template.getMaxEmbeddedVideosPerBooth();
        this.maxPanoramasPerBoothSnapshot = template.getMaxPanoramasPerBooth();
        this.maxHotspotsPerBoothSnapshot = template.getMaxHotspotsPerBooth();
        this.listingPrioritySnapshot = template.getListingPriority();
    }
}
