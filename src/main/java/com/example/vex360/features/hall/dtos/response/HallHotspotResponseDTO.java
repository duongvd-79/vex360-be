package com.example.vex360.features.hall.dtos.response;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallHotspotResponseDTO {
    private UUID id;
    private HallHotspotType type;
    private String name;
    private UUID sourcePanoramaId;
    private UUID targetPanoramaId;
    private String targetPanoramaName;
    private MediaAssetResponseDTO mediaAsset;
    private HallItemSummaryDTO item;
    private Integer boothSlotIndex;
    private HallBoothSummaryDTO targetBooth;
    private String infoText;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
    private String iconStyle;
    private Double scale;
    private Integer zIndex;
    private HotspotMediaClickAction mediaClickAction;
    private HallInfoContentType infoContentType;
    private HallHotspotCornersDTO corners;
}
