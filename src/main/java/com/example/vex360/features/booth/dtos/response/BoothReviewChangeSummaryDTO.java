package com.example.vex360.features.booth.dtos.response;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothReviewComparisonCompleteness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewChangeSummaryDTO {
    private Integer comparisonSchemaVersion;
    private BoothReviewComparisonCompleteness comparisonCompleteness;
    private boolean initialSubmission;
    private Integer versionNumber;
    private UUID comparedToRequestId;
    private Integer comparedToVersionNumber;
    private BoothReviewContentCountsDTO currentCounts;
    private int addedCount;
    private int modifiedCount;
    private int removedCount;
    private int totalCount;
    private List<BoothReviewChangeItemDTO> items;
}
