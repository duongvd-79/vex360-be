package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;

class DesignRequestLifecyclePolicyUnitTest {
    private final DesignRequestLifecyclePolicy policy = new DesignRequestLifecyclePolicy(
            new ExhibitionTimelinePolicy(Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC)));

    @Test
    void completedRequestCreatedByT3GrantsEditUntilT1() {
        DesignRequest request = requestCreatedAt(Instant.parse("2026-01-12T23:59:59Z"));

        policy.grantLateEditWindowIfEligible(request);

        assertEquals(LocalDate.of(2026, 1, 14), request.getBooth().getLateEditAllowedUntil());
    }

    @Test
    void requestCreatedAfterT3DoesNotGrantLateEdit() {
        DesignRequest request = requestCreatedAt(Instant.parse("2026-01-13T00:00:00Z"));

        policy.grantLateEditWindowIfEligible(request);

        assertNull(request.getBooth().getLateEditAllowedUntil());
    }

    private DesignRequest requestCreatedAt(Instant createdAt) {
        Exhibition exhibition = Exhibition.builder().startDate(LocalDate.of(2026, 1, 15)).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Booth booth = Booth.builder().isTemplate(false).exhibitorRegistration(registration).build();
        return DesignRequest.builder().booth(booth).createdAt(createdAt).build();
    }
}
