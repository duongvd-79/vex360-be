package com.example.vex360.shared.entities;

import java.util.UUID;

import com.example.vex360.shared.enums.ExhibitionAssetType;

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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "exhibition_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitionAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Exhibition exhibition;

    @Column(name = "asset_url", nullable = false, length = 1000)
    String assetUrl;

    @Column(name = "public_id", nullable = false, length = 500)
    String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    ExhibitionAssetType type;
}
