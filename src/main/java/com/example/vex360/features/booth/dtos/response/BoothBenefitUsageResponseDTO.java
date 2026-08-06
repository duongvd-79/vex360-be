package com.example.vex360.features.booth.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothBenefitUsageResponseDTO {
    private String packageName;
    private UsageQuota panoramas;
    private UsageQuota hotspots;
    private UsageQuota products;
    private UsageQuota mediaVideos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageQuota {
        private long used;
        private int max;
    }
}
