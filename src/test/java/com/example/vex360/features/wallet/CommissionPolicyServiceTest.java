package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.wallet.dtos.CommissionCalculationResult;
import com.example.vex360.features.wallet.entities.CommissionPolicy;
import com.example.vex360.features.wallet.repositories.CommissionPolicyRepository;
import com.example.vex360.features.wallet.services.CommissionPolicyService;

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
    void calculateFee_DefaultZeroPercent_Success() {
        when(commissionPolicyRepository.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any()))
                .thenReturn(Optional.empty());

        BigDecimal amount = new BigDecimal("1000000");
        CommissionCalculationResult result = commissionPolicyService.calculateCommission(amount, Instant.now());

        assertNotNull(result);
        assertEquals(new BigDecimal("1000000.00"), result.amount());
        assertEquals(new BigDecimal("0.00"), result.systemFee());
        assertEquals(new BigDecimal("1000000.00"), result.organizerPayout());
        assertEquals(0, result.rateBasisPoints());
    }

    @Test
    void calculateFee_TenPercentPolicy_Success() {
        CommissionPolicy policy = CommissionPolicy.builder()
                .rateBasisPoints(1000) // 10.00%
                .effectiveAt(Instant.now().minusSeconds(3600))
                .build();
        when(commissionPolicyRepository.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any()))
                .thenReturn(Optional.of(policy));

        BigDecimal amount = new BigDecimal("1500000");
        CommissionCalculationResult result = commissionPolicyService.calculateCommission(amount, Instant.now());

        assertNotNull(result);
        assertEquals(new BigDecimal("1500000.00"), result.amount());
        assertEquals(new BigDecimal("150000.00"), result.systemFee());
        assertEquals(new BigDecimal("1350000.00"), result.organizerPayout());
        assertEquals(1000, result.rateBasisPoints());
    }

    @Test
    void calculateFee_RoundingHalfUp_Success() {
        CommissionPolicy policy = CommissionPolicy.builder()
                .rateBasisPoints(1050) // 10.50%
                .effectiveAt(Instant.now().minusSeconds(3600))
                .build();
        when(commissionPolicyRepository.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any()))
                .thenReturn(Optional.of(policy));

        BigDecimal amount = new BigDecimal("99999");
        CommissionCalculationResult result = commissionPolicyService.calculateCommission(amount, Instant.now());

        // 99999 * 1050 / 10000 = 10499.895 -> 10499.90
        assertEquals(new BigDecimal("99999.00"), result.amount());
        assertEquals(new BigDecimal("10499.90"), result.systemFee());
        assertEquals(new BigDecimal("89499.10"), result.organizerPayout());
        assertEquals(result.amount(), result.systemFee().add(result.organizerPayout()));
    }
}
