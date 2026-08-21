package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignerWorkspaceResponseDTO {
    private UUID requestId;
    private DesignRequestStatus status;
    private DesignRequestMode mode;
    private DesignRequestCancellationStatus cancellationStatus;
    private Integer remainingDesignActions;
    private Integer requiredProductCount;
    private Integer optionalProductCount;
    private Integer requiredMediaAssetCount;
    private Integer optionalMediaAssetCount;
    private String contactEmail;
    private String contactPhone;
    private String requestNote;
    private String reviewNote;
    private Integer reviewCount;
    private BoothResponseDTO booth;
    private DesignDraftWorkspaceResponseDTO workingDraft;
    private DesignDraftWorkspaceResponseDTO latestSubmittedDraft;
    private Boolean editable;
    private DesignDraftBenefitUsageResponseDTO benefitUsage;
    private StorageUsageResponseDTO storageUsage;
    private DesignDraftStorageMetricsResponseDTO storageMetrics;
}
