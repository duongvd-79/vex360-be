package com.example.vex360.features.hall.dtos.response;

import com.example.vex360.features.hall.services.HallReviewSnapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallReviewRequestDetailDTO {
    private HallReviewRequestSummaryDTO reviewRequest;
    private HallReviewSnapshot contentSnapshot;
}
