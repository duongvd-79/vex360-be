package com.example.vex360.features.exhibition.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerExhibitionSummaryResponseDTO {
    private long pendingRegistrationCount;
    private long pendingBoothReviewCount;
    private long totalCount;
}
