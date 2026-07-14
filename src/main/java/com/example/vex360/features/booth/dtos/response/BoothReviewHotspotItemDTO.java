package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewHotspotItemDTO {
    private UUID id;
    private HotspotType type;
    private String name;
    private UUID sourcePanoramaId;
    private String sourcePanoramaName;
    private UUID targetPanoramaId;
    private String targetPanoramaName;
    private UUID productId;
    private String productName;
    private UUID mediaAssetId;
    private String mediaAssetName;
    private String infoText;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
    private String iconStyle;
    private Double scale;
    private Integer zIndex;
    private HotspotMediaClickAction mediaClickAction;
    private HotspotInfoContentType infoContentType;
    private HotspotCornersDTO corners;
}
