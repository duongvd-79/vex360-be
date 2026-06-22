package com.example.vex360.features.booth.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoothTemplateSummaryResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private BoothStatus status;
    private Boolean isTemplate;
    private Integer panoramaCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
