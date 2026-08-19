package com.example.vex360.features.booth.dtos.request;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoothTemplateHotspotRequest {
    @NotBlank(message = "Tên hotspot không được để trống")
    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự.")
    private String name;

    @NotNull(message = "Panorama đích không được để trống")
    private UUID targetPanoramaId;

    @NotNull(message = "Vị trí x không được để trống")
    private Double xPosition;

    @NotNull(message = "Vị trí y không được để trống")
    private Double yPosition;

    @NotNull(message = "Vị trí z không được để trống")
    private Double zPosition;
}
