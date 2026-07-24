package com.example.vex360.features.booth.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizerBoothSummaryDTO {
    private UUID id;
    private UUID companyId;
    private String ownerName;
    private String packageName;
    private String contactEmail;
    private String contactPhone;
    private String name;
    private String description;
    private String thumbnailUrl;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
    private String displayTemplateKey;
    private BoothStatus status;
    private Instant updatedAt;
}
