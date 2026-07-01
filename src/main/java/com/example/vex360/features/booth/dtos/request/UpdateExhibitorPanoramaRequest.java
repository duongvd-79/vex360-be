package com.example.vex360.features.booth.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExhibitorPanoramaRequest {
    private String name;

    private Integer orderIndex;

    private Boolean isDefault;
}
