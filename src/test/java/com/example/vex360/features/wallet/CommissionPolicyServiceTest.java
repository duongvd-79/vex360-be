package com.example.vex360.features.wallet;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.wallet.entities.CommissionPolicy;
import com.example.vex360.features.wallet.repositories.CommissionPolicyRepository;
import com.example.vex360.features.wallet.services.CommissionPolicyService;
import com.example.vex360.features.user.entities.User;

@ExtendWith(MockitoExtension.class)
class CommissionPolicyServiceTest {

    @Mock
    private CommissionPolicyRepository commissionPolicyRepository;

    @InjectMocks
    private CommissionPolicyService commissionPolicyService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getPolicies_ReturnsAllPolicies() {
        List<CommissionPolicy> policies = List.of(
                CommissionPolicy.builder().rateBasisPoints(1000).build());
        when(commissionPolicyRepository.findAll()).thenReturn(policies);

        assertEquals(policies, commissionPolicyService.getPolicies());
    }

    @Test
    void createPolicyRejectsRatesOutsideAllowedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> commissionPolicyService.createPolicy(-1, Instant.now(), null));
        assertThrows(IllegalArgumentException.class,
                () -> commissionPolicyService.createPolicy(10000, Instant.now(), null));
    }

    @Test
    void createPolicyRejectsDuplicateEffectiveTime() {
        Instant effectiveAt = Instant.parse("2026-01-01T00:00:00Z");
        when(commissionPolicyRepository.existsByEffectiveAt(effectiveAt)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> commissionPolicyService.createPolicy(1000, effectiveAt, null));
    }

    @Test
    void createPolicyUsesCurrentTimeAndPersistsAdmin() {
        User admin = User.builder().build();
        when(commissionPolicyRepository.existsByEffectiveAt(any())).thenReturn(false);
        when(commissionPolicyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CommissionPolicy result = commissionPolicyService.createPolicy(0, null, admin);

        assertEquals(0, result.getRateBasisPoints());
        assertEquals(admin, result.getCreatedBy());
        assertNotNull(result.getEffectiveAt());
    }
}
