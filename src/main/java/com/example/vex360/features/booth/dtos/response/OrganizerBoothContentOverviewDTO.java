package com.example.vex360.features.booth.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizerBoothContentOverviewDTO {
    private OrganizerBoothSummaryDTO booth;
    private OrganizerBoothReviewContextDTO reviewContext;
    private BoothReviewContentOverviewDTO contentOverview;
}
