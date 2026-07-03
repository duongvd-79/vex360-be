package com.example.vex360.features.booth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExhibitorPanoramaRequest {
    @NotBlank(message = "Ten panorama khong duoc de trong")
    private String name;

    private Integer orderIndex;

    private Boolean isDefault;
}
