package com.example.vex360.features.booth.dtos.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanoramaResponseDTO {
    private UUID id;
    private String name;
    private String imageUrl;
    private Integer orderIndex;
    private Boolean isDefault;
    private List<HotspotResponseDTO> hotspots;
}
