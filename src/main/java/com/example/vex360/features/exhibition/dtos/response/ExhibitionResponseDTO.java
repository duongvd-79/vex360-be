package com.example.vex360.features.exhibition.dtos.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExhibitionResponseDTO {
    private Integer id; // Only visible internally
    private UUID uuid;
    private String name;
    private String category;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer estimatedBooths;
    private String status;
    private String organizerName;
    private String rejectedReason;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private List<ExhibitionPackageResponseDTO> packages;

    private String keyVisualUrl;
    private String trailerVideoUrl;
    private String floorPlanUrl;
    private String guidelineUrl;
}
