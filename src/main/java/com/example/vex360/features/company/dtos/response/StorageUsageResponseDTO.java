package com.example.vex360.features.company.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageUsageResponseDTO {
    Long usedBytes;
    Long quotaBytes;
    Double usedPercentage;
}
