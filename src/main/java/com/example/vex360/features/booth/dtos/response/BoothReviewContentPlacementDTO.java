package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewContentPlacementDTO {
    private UUID panoramaId;
    private String panoramaName;
    private UUID hotspotId;
    private String hotspotName;
}
