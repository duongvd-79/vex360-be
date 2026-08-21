package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizerBoothReviewContextDTO {
    private UUID pendingReviewRequestId;
    private Integer pendingReviewVersion;
    private boolean canApprove;
    private boolean canReject;
}
