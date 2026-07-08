package com.example.vex360.features.booth.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewRequestDetailDTO {
    private BoothReviewRequestSummaryDTO request;
    private BoothResponseDTO booth;
    private BoothReviewContentOverviewDTO contentOverview;
    private BoothReviewChangeSummaryDTO changeSummary;
}
