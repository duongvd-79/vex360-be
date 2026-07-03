package com.example.vex360.features.booth.dtos.request;

import java.util.UUID;

<<<<<<< HEAD
import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
=======
>>>>>>> 1787f98 (Merge branch 'dev' of https://github.com/duongvd-79/vex360-be into Myntt)
import com.example.vex360.features.booth.enums.HotspotType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertHotspotRequest {
    @NotNull(message = "Loai hotspot khong duoc de trong")
    private HotspotType type;

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

    private String infoText;

    private String iconStyle;

    private Double scale;

    private Integer zIndex;

    private HotspotMediaClickAction mediaClickAction;

    private HotspotInfoContentType infoContentType;

    private HotspotCornersDTO corners;
}
