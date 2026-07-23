package com.example.vex360.features.designrequest.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftBenefitMetricResponseDTO {
    private Integer limit;
    private Integer baseline;
    private Integer working;
    private Integer remaining;
}
