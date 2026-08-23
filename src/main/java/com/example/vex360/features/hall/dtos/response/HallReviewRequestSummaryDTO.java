package com.example.vex360.features.hall.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.hall.enums.HallReviewStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallReviewRequestSummaryDTO {
    private UUID id;
    private Integer versionNumber;
    private UUID hallId;
    private String hallName;
    private UUID exhibitionUuid;
    private String exhibitionName;
    private HallReviewStatus status;
    private UUID submittedById;
    private String submittedByName;
    private Instant submittedAt;
    private UUID reviewedById;
    private String reviewedByName;
    private Instant reviewedAt;
    private String rejectedReason;
    private HallReviewChangeSummaryDTO changeSummary;
}
