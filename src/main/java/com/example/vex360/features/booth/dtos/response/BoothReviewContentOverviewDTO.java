package com.example.vex360.features.booth.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewContentOverviewDTO {
    private Integer panoramaCount;
    private Integer hotspotCount;
    private Integer productCount;
    private Integer mediaAssetCount;
    private List<BoothReviewPanoramaItemDTO> panoramas;
    private List<BoothReviewProductItemDTO> products;
    private List<BoothReviewMediaItemDTO> mediaAssets;
}
