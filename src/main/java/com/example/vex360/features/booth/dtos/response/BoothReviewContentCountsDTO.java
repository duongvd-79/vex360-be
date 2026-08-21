package com.example.vex360.features.booth.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewContentCountsDTO {
    private int boothCount;
    private int panoramaCount;
    private int hotspotCount;
    private int productCount;
    private int productContentCount;
    private int mediaAssetCount;
    private int productPlacementCount;
    private int mediaPlacementCount;
}
