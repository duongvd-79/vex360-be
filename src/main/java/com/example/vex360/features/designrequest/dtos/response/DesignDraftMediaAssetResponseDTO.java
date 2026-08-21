package com.example.vex360.features.designrequest.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftMediaAssetResponseDTO {
    private UUID id;
    private UUID draftId;
    private UUID assetId;
    private String url;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private DesignDraftAssetType assetType;
    private String title;
    private Integer sortOrder;
    private Instant createdAt;
}
