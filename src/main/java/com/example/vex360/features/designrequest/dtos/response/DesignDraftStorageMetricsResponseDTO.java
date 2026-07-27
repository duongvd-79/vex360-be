package com.example.vex360.features.designrequest.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftStorageMetricsResponseDTO {
    private Long projectedTotalStorageBytes;
    private Long projectedNewAssetsStorageBytes;
    private Long exhibitorAvailableStorageBytes;
}
