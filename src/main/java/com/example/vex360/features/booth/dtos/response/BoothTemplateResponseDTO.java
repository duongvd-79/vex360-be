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
public class BoothTemplateResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private BoothStatus status;
    private Boolean isTemplate;
    private UUID createdById;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PanoramaResponseDTO> panoramas;
}
