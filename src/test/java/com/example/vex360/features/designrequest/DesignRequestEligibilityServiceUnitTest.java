package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.product.enums.ProductStatus;

@ExtendWith(MockitoExtension.class)
class DesignRequestEligibilityServiceUnitTest {
    @Mock
    PanoramaRepository panoramaRepository;
    @Mock
    HotspotRepository hotspotRepository;
    @Mock
    DesignRequestRepository requestRepository;

    private DesignRequestEligibilityService service;
    private Booth booth;

    @BeforeEach
    void setup() {
        service = new DesignRequestEligibilityService(panoramaRepository, hotspotRepository, requestRepository);
        booth = Booth.builder().id(UUID.randomUUID()).name("Booth").status(BoothStatus.DRAFT).build();
    }

    @Test
    void emptyBoothIsEligibleForInitialDesign() {
        DesignRequestEligibilityResponseDTO result = service.evaluate(booth);

        assertEquals(DesignRequestMode.INITIAL_DESIGN, result.getMode());
        assertTrue(result.isEligible());
        assertEquals(3, result.getRemainingDesignActions());
    }

    @Test
    void redesignIsIneligibleWhenBaselineUsesInactiveProduct() {
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);
        when(hotspotRepository.existsBySourcePanoramaBoothIdAndProductStatusNot(
                booth.getId(), ProductStatus.ACTIVE)).thenReturn(true);

        DesignRequestEligibilityResponseDTO result = service.evaluate(booth);

        assertEquals(DesignRequestMode.REDESIGN, result.getMode());
        assertFalse(result.isEligible());
        assertEquals(DesignRequestEligibilityService.INACTIVE_BASELINE_PRODUCT, result.getReasonCode());
    }

    @Test
    void refundedPendingCancellationDoesNotConsumeQuota() {
        when(requestRepository.countByBoothIdAndQuotaChargedTrue(booth.getId())).thenReturn(1L);
        when(requestRepository.sumReviewCountByBoothId(booth.getId())).thenReturn(1L);

        assertEquals(1, service.remainingActions(booth));
    }
}
