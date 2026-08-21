package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.vex360.shared.enums.ExhibitionStatus;

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
public class ExhibitionWalletSummaryDTO {
    UUID exhibitionUuid;
    String exhibitionName;
    ExhibitionStatus status;
    LocalDate startDate;
    LocalDate endDate;
    long paidRegistrationCount;
    BigDecimal grossRevenue;
    BigDecimal systemFeeTotal;
    BigDecimal organizerNetRevenue;
    BigDecimal pendingBalance;
    BigDecimal availableBalance;
    BigDecimal reversedAmount;
}
