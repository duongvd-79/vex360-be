package com.example.vex360.features.hall.dtos.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HallReviewChangeSummaryDTO {
    private Boolean initialSubmission;
    private Integer versionNumber;
    private UUID comparedToRequestId;
    private Integer comparedToVersionNumber;
    private Integer panoramaCount;
    private Integer hotspotCount;
    private Integer itemCount;
    private Integer mediaAssetCount;
    private List<String> changedSections;
}
