package com.example.vex360.features.booth.dtos.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.shared.enums.BoothListingPriority;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoothResponseDTO {
    private UUID id;
    private UUID companyId;
    private UUID registrationUuid;
    private UUID exhibitionUuid;
    private String exhibitionName;
    private String name;
    private String description;
    private String thumbnailUrl;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
    private String displayTemplateKey;
    private BoothListingPriority listingPriority;
    private BoothStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String companyName;
    private String companyIndustry;
    private String companyEmail;
    private List<PanoramaResponseDTO> panoramas;
}
