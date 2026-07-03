package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import com.example.vex360.features.booth.enums.HotspotType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotResponseDTO {
    private UUID id;
    private HotspotType type;
    private String name;
    private UUID sourcePanoramaId;
    private UUID targetPanoramaId;
    private String targetPanoramaName;
    private HotspotPanoramaSummaryDTO targetPanorama;
    private HotspotProductSummaryDTO product;
    private MediaAssetResponseDTO mediaAsset;
    private String infoText;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
    private String iconStyle;
    private Double scale;
    private Integer zIndex;
}
