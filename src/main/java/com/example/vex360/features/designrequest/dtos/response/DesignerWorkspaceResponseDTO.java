package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.shared.enums.DesignRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignerWorkspaceResponseDTO {
    private UUID requestId;
    private DesignRequestStatus status;
    private String requestNote;
    private String reviewNote;
    private Integer reviewCount;
    private BoothResponseDTO booth;
    private DesignDraftWorkspaceResponseDTO workingDraft;
    private DesignDraftWorkspaceResponseDTO latestSubmittedDraft;
}
