package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignerWorkloadResponseDTO {
    private UUID designerId;
    private String designerName;
    private String designerEmail;
    private Long workingRequests;
    private Long waitingReviewRequests;
    private Long queuedRevisionRequests;
    private Integer availableSlots;
}
