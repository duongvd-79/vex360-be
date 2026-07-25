package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExhibitorDesignReviewWorkspaceResponseDTO {
    private UUID requestId;
    private DesignRequestStatus status;
    private DesignRequestMode mode;
    private Integer remainingDesignActions;
    private BoothResponseDTO currentBooth;
    private DesignDraftWorkspaceResponseDTO latestSubmittedDraft;
    private List<ProductResponseDTO> requiredProducts;
    private List<ProductResponseDTO> optionalProducts;
    private List<MediaAssetResponseDTO> referencedMedia;
    private List<DesignDraftMediaAssetResponseDTO> referencedDraftMedia;
    private StorageUsageResponseDTO storageUsage;
    private DesignDraftStorageMetricsResponseDTO storageMetrics;
    private BoothReviewContentOverviewDTO contentOverview;
    private List<DesignDraftSubmissionHistoryItemDTO> submissionHistory;
    private DesignDraftChangeSummaryDTO changeSummary;
}
