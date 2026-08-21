package com.example.vex360.features.booth.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.booth.enums.MediaAssetType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaAssetResponseDTO {
    private UUID id;
    private UUID companyId;
    private String name;
    private MediaAssetType type;
    private String url;
    private String publicId;
    private String mimeType;
    private Long fileSize;
    private Instant createdAt;
}
