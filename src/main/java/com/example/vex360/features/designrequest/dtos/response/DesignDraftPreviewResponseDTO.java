package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftPreviewSource;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.shared.enums.DesignRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftPreviewResponseDTO {
    private UUID requestId;
    private DesignRequestMode mode;
    private DesignRequestStatus status;
    private DesignDraftPreviewSource source;
    private Boolean editable;
    private UUID draftId;
    private Integer versionNumber;
    private DesignDraftBenefitUsageResponseDTO benefitUsage;
    private DesignDraftPreviewBoothResponseDTO booth;
}
