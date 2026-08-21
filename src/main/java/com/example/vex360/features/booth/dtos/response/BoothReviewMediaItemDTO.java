package com.example.vex360.features.booth.dtos.response;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.MediaAssetType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewMediaItemDTO {
    private UUID id;
    private String name;
    private MediaAssetType type;
    private String url;
    private String mimeType;
    private Long fileSize;
    private Integer usageCount;
    private List<BoothReviewContentPlacementDTO> placements;
}
