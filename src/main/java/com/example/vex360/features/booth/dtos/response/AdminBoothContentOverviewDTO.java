package com.example.vex360.features.booth.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminBoothContentOverviewDTO {
    private BoothResponseDTO booth;
    private BoothReviewContentOverviewDTO contentOverview;
    private AdminBoothModerationDTO moderation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminBoothModerationDTO {
        private BoothStatus status;
        private Integer warningCount;
        private String warningReason;
        private Instant warnedAt;
        private UUID warnedById;
        private String warnedByName;
        private String banReason;
        private Instant bannedAt;
        private UUID bannedById;
        private String bannedByName;
        private Boolean canWarn;
        private Boolean canBan;
        private String cannotWarnReason;
    }
}
