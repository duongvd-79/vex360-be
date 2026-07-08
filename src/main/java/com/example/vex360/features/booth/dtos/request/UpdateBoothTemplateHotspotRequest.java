package com.example.vex360.features.booth.dtos.request;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothTemplateHotspotRequest {
    private String name;
    private UUID targetPanoramaId;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
}
