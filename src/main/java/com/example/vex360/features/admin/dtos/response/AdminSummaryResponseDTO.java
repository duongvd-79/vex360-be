package com.example.vex360.features.admin.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSummaryResponseDTO {
    private long pendingPartnershipRequests;
    private long pendingDesignRequests;
    private long pendingExhibitionRequests;
}
