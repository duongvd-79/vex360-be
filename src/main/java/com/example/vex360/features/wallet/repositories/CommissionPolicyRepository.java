package com.example.vex360.features.wallet.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.vex360.features.wallet.entities.CommissionPolicy;

public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicy, Long> {

    @Override
    @EntityGraph(attributePaths = "createdBy")
    List<CommissionPolicy> findAll();

    Optional<CommissionPolicy> findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant time);

    boolean existsByEffectiveAt(Instant effectiveAt);
}
