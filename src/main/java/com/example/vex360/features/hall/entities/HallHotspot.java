package com.example.vex360.features.hall.entities;

import java.util.UUID;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "hall_hotspots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HallHotspot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_panorama_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    HallPanorama sourcePanorama;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(50)")
    HallHotspotType type;

    @Column(name = "name", nullable = false)
    String name;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_panorama_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    HallPanorama targetPanorama;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    MediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    HallItem item;

    @Column(name = "booth_slot_index")
    Integer boothSlotIndex;

    @Column(name = "info_text", columnDefinition = "TEXT")
    String infoText;

    @Enumerated(EnumType.STRING)
    @Column(name = "info_content_type", columnDefinition = "VARCHAR(50)")
    HallInfoContentType infoContentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_click_action", columnDefinition = "VARCHAR(50)")
    HotspotMediaClickAction mediaClickAction;

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
}
