package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftPanoramaResponseDTO {
    private UUID id;
    private String clientKey;
    private String name;
    private String imageUrl;
    private String imageKey;
    private Integer orderIndex;
    private Boolean isDefault;
    private List<DesignDraftHotspotResponseDTO> hotspots;
}
