package com.example.vex360.features.designrequest.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftAssetResponseDTO {
    private UUID id;
    private UUID requestId;
    private String url;
    private String imageKey;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private DesignDraftAssetType assetType;
    private DesignDraftAssetSource assetSource;
    private Instant createdAt;
}
