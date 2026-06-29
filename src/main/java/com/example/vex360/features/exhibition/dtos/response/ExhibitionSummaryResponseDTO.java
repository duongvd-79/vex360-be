package com.example.vex360.features.exhibition.dtos.response;

import java.util.Map;

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
public class ExhibitionSummaryResponseDTO {
    long totalExhibitions;
    long pendingExhibitions;
    long approvedExhibitions;
    long rejectedExhibitions;
    long activeExhibitions;
    Map<String, Long> statusCounts;
}
