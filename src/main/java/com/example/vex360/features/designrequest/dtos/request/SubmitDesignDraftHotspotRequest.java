package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftHotspotRequest {
    @NotNull(message = "Loại hotspot không được để trống")
    private HotspotType type;

    private String name;

    @NotNull(message = "Vị trí x không được để trống")
    private Double xPosition;

    @NotNull(message = "Vị trí y không được để trống")
    private Double yPosition;

    @NotNull(message = "Vị trí z không được để trống")
    private Double zPosition;

    private String targetDraftPanoramaKey;

    private UUID productId;

    private UUID mediaAssetId;

    private String infoText;

    private String iconStyle;

    private Double scale;

    private Integer zIndex;

    private HotspotMediaClickAction mediaClickAction;

    private HotspotInfoContentType infoContentType;

    private HotspotCornersDTO corners;
}
