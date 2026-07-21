package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignAssignmentCandidateResponseDTO {
    private UUID designerId;
    private String designerName;
    private String designerEmail;
    private long workingRequests;
    private int availableSlots;
}
