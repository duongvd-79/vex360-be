package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDesignDraftPanoramaRequest {
    @NotBlank(message = "Ten panorama khong duoc de trong")
    private String name;

    @NotNull(message = "Panorama asset khong duoc de trong")
    private UUID panoramaAssetId;

    private Integer orderIndex;
    private Boolean isDefault;
}
