package com.example.vex360.features.booth.dtos.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;

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
    private String name;
    private String description;
    private String thumbnailUrl;
    private String displayTemplateKey;
    private BoothStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PanoramaResponseDTO> panoramas;
}
