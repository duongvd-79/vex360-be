package com.example.vex360.features.designrequest.dtos.response;

import java.time.Instant;
import com.example.vex360.shared.enums.DesignRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignDraftSubmissionHistoryItemDTO {
    private Integer versionNumber;
    private Instant submittedAt;
    private String designerNote;
    private DesignRequestStatus reviewStatus;
    private String rejectionReason;
    private int panoramaCount;
    private int hotspotCount;
}
