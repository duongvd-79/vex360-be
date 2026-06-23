package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHotspotRequest {
    @NotBlank(message = "Ten hotspot khong duoc de trong")
    private String name;

    @NotBlank(message = "Panorama dich khong duoc de trong")
    private String targetPanoramaKey;

    @NotNull(message = "Vi tri x khong duoc de trong")
    private Double xPosition;

    @NotNull(message = "Vi tri y khong duoc de trong")
    private Double yPosition;

    @NotNull(message = "Vi tri z khong duoc de trong")
    private Double zPosition;
}
