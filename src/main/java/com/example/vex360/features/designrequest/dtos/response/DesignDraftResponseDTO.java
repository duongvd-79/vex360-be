package com.example.vex360.features.designrequest.dtos.response;

import java.time.Instant;
import java.util.UUID;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftResponseDTO {
    private UUID id;
    private Integer versionNumber;
    private String note;
    private List<DesignDraftMediaAssetResponseDTO> mediaAssets;
    private Integer mediaAssetCount;
    private Long mediaAssetTotalBytes;
    private Instant createdAt;
}
