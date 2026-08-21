package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftHotspotRequest {
    @NotNull(message = "Loại hotspot không được để trống")
    private HotspotType type;

    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vị trí x không được để trống")
    private Double xPosition;

    @NotNull(message = "Vị trí y không được để trống")
    private Double yPosition;

    @NotNull(message = "Vị trí z không được để trống")
    private Double zPosition;

    @Size(max = 100, message = "Panorama đích không được vượt quá 100 ký tự.")
    private String targetDraftPanoramaKey;

    private UUID productId;

    private UUID mediaAssetId;

    private UUID designDraftMediaAssetId;

    @Size(max = 5000, message = "Nội dung thông tin không được vượt quá 5000 ký tự.")
    private String infoText;

    @Size(max = 100, message = "Kiểu icon không được vượt quá 100 ký tự.")
    private String iconStyle;

    private Double scale;

    private Integer zIndex;

    private HotspotMediaClickAction mediaClickAction;

    private HotspotInfoContentType infoContentType;

    @Valid
    private HotspotCornersDTO corners;
}
