package com.example.vex360.features.designrequest.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignerDesignRequestSummaryResponseDTO {
    private long assignedCount;
    private long revisionRequestedCount;
}
