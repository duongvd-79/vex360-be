package com.example.vex360.features.wallet.services;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.entities.CommissionPolicy;
import com.example.vex360.features.wallet.repositories.CommissionPolicyRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommissionPolicyService {

    CommissionPolicyRepository commissionPolicyRepository;

    @Transactional(readOnly = true)
    public List<CommissionPolicy> getPolicies() {
        return commissionPolicyRepository.findAll();
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
