package com.example.vex360.features.booth.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothModerationSummaryDTO {
    private UUID boothId;
    private String boothName;
    private String companyName;
    private BoothStatus boothStatus;
    private Integer warningCount;
    private String warningReason;
    private Instant warnedAt;
    private String banReason;
    private Instant bannedAt;
    private String latestAction;
    private String latestReason;
    private Instant latestActionAt;
}
