package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReconciliationReportDTO {
    UUID companyId;
    String companyName;
    boolean match;

    BigDecimal pendingSnapshot;
    BigDecimal pendingLedger;
    BigDecimal pendingDelta;

    BigDecimal availableSnapshot;
    BigDecimal availableLedger;
    BigDecimal availableDelta;

    BigDecimal reservedSnapshot;
    BigDecimal reservedLedger;
    BigDecimal reservedDelta;

    BigDecimal withdrawnSnapshot;
    BigDecimal withdrawnLedger;
    BigDecimal withdrawnDelta;
}
