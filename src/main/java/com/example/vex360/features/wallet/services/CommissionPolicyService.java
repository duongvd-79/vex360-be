package com.example.vex360.features.wallet.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.services.CommissionCalculator;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.dtos.CommissionCalculationResult;
import com.example.vex360.features.wallet.entities.CommissionPolicy;
import com.example.vex360.features.wallet.repositories.CommissionPolicyRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommissionPolicyService implements CommissionCalculator {

    CommissionPolicyRepository commissionPolicyRepository;

    @Transactional(readOnly = true)
    public List<CommissionPolicy> getPolicies() {
        return commissionPolicyRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionResult calculateCommission(BigDecimal amount, Instant time) {
        CommissionCalculationResult result = calculateCommissionResult(amount, time);
        return new CommissionResult(result.amount(), result.systemFee(), result.organizerPayout(), result.rateBasisPoints());
    }

    @Transactional(readOnly = true)
    public CommissionCalculationResult calculateCommissionResult(BigDecimal amount, Instant time) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        Instant targetTime = time != null ? time : Instant.now();
        Integer bps = commissionPolicyRepository
                .findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(targetTime)
                .map(CommissionPolicy::getRateBasisPoints)
                .orElse(0);

        BigDecimal fee;
        if (bps == null || bps <= 0) {
            fee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            fee = amount.multiply(BigDecimal.valueOf(bps))
                    .divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
        }

        BigDecimal payout = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        return new CommissionCalculationResult(amount, fee, payout, bps);
    }

    @Transactional
    public CommissionPolicy createPolicy(int rateBasisPoints, Instant effectiveAt, User adminUser) {
        if (rateBasisPoints < 0 || rateBasisPoints > 9999) {
            throw new IllegalArgumentException("Rate basis points must be between 0 and 9999 (0% to 99.99%)");
        }
        Instant targetEffectiveAt = effectiveAt != null ? effectiveAt : Instant.now();
        if (commissionPolicyRepository.existsByEffectiveAt(targetEffectiveAt)) {
            throw new IllegalArgumentException("A policy with effectiveAt " + targetEffectiveAt + " already exists");
        }

        CommissionPolicy policy = CommissionPolicy.builder()
                .rateBasisPoints(rateBasisPoints)
                .effectiveAt(targetEffectiveAt)
                .createdBy(adminUser)
                .build();

        return commissionPolicyRepository.save(policy);
    }
}
