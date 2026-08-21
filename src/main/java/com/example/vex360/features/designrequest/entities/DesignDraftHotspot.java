package com.example.vex360.features.designrequest.entities;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.product.entities.Product;

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
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "design_draft_hotspots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DesignDraftHotspot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_draft_panorama_id", nullable = false)
    DesignDraftPanorama sourcePanorama;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(50)")
    HotspotType type;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "target_draft_panorama_key", length = 100)
    String targetDraftPanoramaKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id")
    MediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_draft_media_asset_id")
    DesignDraftMediaAsset designDraftMediaAsset;

    @Column(name = "info_text", columnDefinition = "TEXT")
    String infoText;

    @Column(name = "x_position", nullable = false)
    Double xPosition;

    @Column(name = "y_position", nullable = false)
    Double yPosition;

    @Column(name = "z_position", nullable = false)
    Double zPosition;

    @Column(name = "icon_style", length = 100)
    String iconStyle;

    @Column(name = "scale")
    Double scale;

    @Column(name = "z_index")
    Integer zIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_click_action", columnDefinition = "VARCHAR(50)")
    HotspotMediaClickAction mediaClickAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "info_content_type", columnDefinition = "VARCHAR(50)")
    HotspotInfoContentType infoContentType;

    @Column(name = "corner_tl_x")
    Double cornerTlX;

    @Column(name = "corner_tl_y")
    Double cornerTlY;

    @Column(name = "corner_tl_z")
    Double cornerTlZ;

    @Column(name = "corner_tr_x")
    Double cornerTrX;

    @Column(name = "corner_tr_y")
    Double cornerTrY;

    @Column(name = "corner_tr_z")
    Double cornerTrZ;

    @Column(name = "corner_bl_x")
    Double cornerBlX;

    @Column(name = "corner_bl_y")
    Double cornerBlY;

    @Column(name = "corner_bl_z")
    Double cornerBlZ;

    @Column(name = "corner_br_x")
    Double cornerBrX;

    @Column(name = "corner_br_y")
    Double cornerBrY;

    @Column(name = "corner_br_z")
    Double cornerBrZ;

    @Transient
    public HotspotCornersDTO getCorners() {
        if (cornerTlX == null || cornerTlY == null || cornerTlZ == null
                || cornerTrX == null || cornerTrY == null || cornerTrZ == null
                || cornerBlX == null || cornerBlY == null || cornerBlZ == null
                || cornerBrX == null || cornerBrY == null || cornerBrZ == null) {
            return null;
        }
        return new HotspotCornersDTO(
                List.of(cornerTlX, cornerTlY, cornerTlZ),
                List.of(cornerTrX, cornerTrY, cornerTrZ),
                List.of(cornerBlX, cornerBlY, cornerBlZ),
                List.of(cornerBrX, cornerBrY, cornerBrZ));
    }
}
