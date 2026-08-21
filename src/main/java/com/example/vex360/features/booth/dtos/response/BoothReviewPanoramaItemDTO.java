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
public class BoothReviewPanoramaItemDTO {
    private UUID id;
    private String name;
    private String imageUrl;
    private Integer orderIndex;
    private Boolean isDefault;
    private Long fileSize;
    private Integer hotspotCount;
}
