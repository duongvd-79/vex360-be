package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignAssignmentAnalyticsResponseDTO {
    private Long pendingRequests;
    private Long workingRequests;
    private Long waitingReviewRequests;
    private Long approvedRequests;
    private Long canceledRequests;
    private List<DesignerWorkloadResponseDTO> designerWorkloads;
}
