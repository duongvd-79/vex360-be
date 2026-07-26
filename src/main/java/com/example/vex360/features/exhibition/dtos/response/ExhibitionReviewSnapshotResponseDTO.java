package com.example.vex360.features.exhibition.dtos.response;

import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExhibitionReviewSnapshotResponseDTO {
    String name;
    String category;
    String description;
    LocalDate startDate;
    LocalDate endDate;
    Integer estimatedBooths;
    String keyVisualUrl;
}
