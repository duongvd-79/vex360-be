package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignDraftChangeSummaryDTO {
    private int addedCount;
    private int modifiedCount;
    private int removedCount;
    private int totalCount;
    private List<DesignDraftChangeItemDTO> items;
}
