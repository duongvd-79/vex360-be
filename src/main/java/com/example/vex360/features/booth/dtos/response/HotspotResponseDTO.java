package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotResponseDTO {
    private UUID id;
    private String name;
    private UUID sourcePanoramaId;
    private UUID targetPanoramaId;
    private String targetPanoramaName;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
}
