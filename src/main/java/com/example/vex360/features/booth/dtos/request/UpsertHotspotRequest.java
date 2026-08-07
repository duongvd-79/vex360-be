package com.example.vex360.features.booth.dtos.request;

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
public class UpsertHotspotRequest {
    @NotNull(message = "Loai hotspot khong duoc de trong")
    private HotspotType type;

    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vi tri x khong duoc de trong")
    private Double xPosition;

    @NotNull(message = "Vi tri y khong duoc de trong")
    private Double yPosition;

    @NotNull(message = "Vi tri z khong duoc de trong")
    private Double zPosition;

    private UUID targetPanoramaId;

    private UUID productId;

    private UUID mediaAssetId;

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
