package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;

public record CommissionCalculationResult(
        BigDecimal amount,
        BigDecimal systemFee,
        BigDecimal organizerPayout,
        Integer rateBasisPoints) {
}
