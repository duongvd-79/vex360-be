package com.example.vex360.features.designrequest.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftBenefitUsageResponseDTO {
    private DesignDraftBenefitMetricResponseDTO panoramas;
    private DesignDraftBenefitMetricResponseDTO hotspots;
    private DesignDraftBenefitMetricResponseDTO products;
    private DesignDraftBenefitMetricResponseDTO embeddedVideos;
}
