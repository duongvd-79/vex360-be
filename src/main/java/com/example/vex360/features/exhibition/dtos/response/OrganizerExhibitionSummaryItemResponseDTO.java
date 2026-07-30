package com.example.vex360.features.exhibition.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerExhibitionSummaryItemResponseDTO {
    private UUID exhibitionUuid;
    private String exhibitionName;
    private long pendingRegistrationCount;
    private long pendingBoothReviewCount;
    private long totalCount;
}
