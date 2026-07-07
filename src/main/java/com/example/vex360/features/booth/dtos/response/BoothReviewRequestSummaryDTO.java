package com.example.vex360.features.booth.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothReviewStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewRequestSummaryDTO {
    private UUID id;
    private UUID boothId;
    private String boothName;
    private UUID companyId;
    private String companyName;
    private UUID exhibitionUuid;
    private String exhibitionName;
    private BoothReviewStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String rejectedReason;
}
