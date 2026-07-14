package com.example.vex360.features.booth.dtos.response;

import java.time.LocalDateTime;
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
    private String name;
    private String description;
    private String thumbnailUrl;
    private String backgroundMusicUrl;
    private String displayTemplateKey;
    private BoothStatus status;
    private LocalDateTime updatedAt;
}
