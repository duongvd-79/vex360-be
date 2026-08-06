package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHotspotRequest {
    @NotBlank(message = "Ten hotspot khong duoc de trong")
    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự.")
    private String name;

    @NotBlank(message = "Panorama dich khong duoc de trong")
    @Size(max = 100, message = "Panorama đích không được vượt quá 100 ký tự.")
    private String targetPanoramaKey;

    @NotNull(message = "Vi tri x khong duoc de trong")
    private Double xPosition;

    @NotNull(message = "Vi tri y khong duoc de trong")
    private Double yPosition;

    @NotNull(message = "Vi tri z khong duoc de trong")
    private Double zPosition;
}
