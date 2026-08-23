package com.example.vex360.features.hall.dtos.request;

import java.util.UUID;

import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.hall.dtos.HallHotspotCornersDTO;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertHallHotspotRequest {
    @NotNull(message = "Loại hotspot không được để trống")
    private HallHotspotType type;

    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự")
    private String name;

    @NotNull(message = "Vị trí x không được để trống")
    private Double xPosition;

    @NotNull(message = "Vị trí y không được để trống")
    private Double yPosition;

    @NotNull(message = "Vị trí z không được để trống")
    private Double zPosition;

    private UUID targetPanoramaId;
    private UUID mediaAssetId;
    private UUID itemId;

    @PositiveOrZero(message = "Thứ tự vị trí gian hàng phải lớn hơn hoặc bằng 0")
    private Integer boothSlotIndex;

    @Size(max = 5000, message = "Nội dung thông tin không được vượt quá 5000 ký tự")
    private String infoText;

    @Size(max = 100, message = "Kiểu icon không được vượt quá 100 ký tự")
    private String iconStyle;

    private Double scale;
    private Integer zIndex;
    private HotspotMediaClickAction mediaClickAction;
    private HallInfoContentType infoContentType;

    @Valid
    private HallHotspotCornersDTO corners;
}
