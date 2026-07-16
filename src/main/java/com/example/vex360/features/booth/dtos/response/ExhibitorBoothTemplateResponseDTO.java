package com.example.vex360.features.booth.dtos.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitorBoothTemplateResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private String thumbnailUrl;
    private Long panoramaCount;
    private Long hotspotCount;
    private List<PanoramaResponseDTO> panoramas;
}
