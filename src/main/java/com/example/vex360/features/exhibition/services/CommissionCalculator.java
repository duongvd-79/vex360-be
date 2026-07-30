package com.example.vex360.features.exhibition.services;

import java.math.BigDecimal;
import java.time.Instant;

public interface CommissionCalculator {

    CommissionResult calculateCommission(BigDecimal amount, Instant time);

    record CommissionResult(
            BigDecimal amount,
            BigDecimal systemFee,
            BigDecimal organizerPayout,
            Integer rateBasisPoints
    ) {}
}
