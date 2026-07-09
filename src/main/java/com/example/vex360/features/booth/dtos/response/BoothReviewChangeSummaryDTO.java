package com.example.vex360.features.booth.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewChangeSummaryDTO {
    private boolean initialSubmission;
    private int addedCount;
    private int modifiedCount;
    private int removedCount;
    private int totalCount;
    private List<BoothReviewChangeItemDTO> items;
}
