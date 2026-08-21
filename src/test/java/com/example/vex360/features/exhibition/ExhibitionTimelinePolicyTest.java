package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.shared.enums.ExhibitionStatus;

class ExhibitionTimelinePolicyTest {

    private Clock clock;
    private ExhibitionTimelinePolicy policy;

    @BeforeEach
    void setUp() {
        // Fixed time at 2026-01-10T10:00:00Z -> today is 2026-01-10
        clock = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);
        policy = new ExhibitionTimelinePolicy(clock);
    }

    @Test
    @DisplayName("Should return T-3 as booth review deadline")
    void testGetBoothReviewDeadline() {
        Exhibition exhibition = Exhibition.builder()
                .startDate(LocalDate.of(2026, Month.JANUARY, 15))
                .build();

        LocalDate deadline = policy.getBoothReviewDeadline(exhibition);
        assertEquals(LocalDate.of(2026, Month.JANUARY, 12), deadline);
    }

    @Test
    @DisplayName("Should allow booth preparation on and before T-3 deadline date")
    void testIsBoothPreparationOpen() {
        // Start date is Jan 13 -> deadline T-3 is Jan 10 (today is Jan 10)
        Exhibition exhibition = Exhibition.builder()
                .startDate(LocalDate.of(2026, Month.JANUARY, 13))
                .build();

        assertTrue(policy.isBoothPreparationOpen(exhibition));

        // Start date is Jan 12 -> deadline T-3 is Jan 9 (today Jan 10 is AFTER
        // deadline)
        exhibition.setStartDate(LocalDate.of(2026, Month.JANUARY, 12));
        assertFalse(policy.isBoothPreparationOpen(exhibition));
    }

    @Test
    @DisplayName("Should validate minimum lead time")
    void testHasMinimumLeadTime() {
        // Today is Jan 10 -> min start date with default 31 days is Feb 10
        assertTrue(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.FEBRUARY, 10)));
        assertTrue(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.FEBRUARY, 20)));
        assertFalse(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.FEBRUARY, 9)));
        assertFalse(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 10)));

        // Custom 7 days
        assertTrue(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 17), 7));
        assertFalse(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 16), 7));
    }

    @Test
    @DisplayName("Should validate registration is open when status is REGISTRATION or PUBLISHED and before T-3 deadline")
    void testIsRegistrationOpen() {
        Exhibition exhibition = Exhibition.builder()
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.JANUARY, 15))
                .build();

        assertTrue(policy.isRegistrationOpen(exhibition));

        exhibition.setStatus(ExhibitionStatus.PUBLISHED);
        assertTrue(policy.isRegistrationOpen(exhibition));

        exhibition.setStatus(ExhibitionStatus.PENDING);
        assertFalse(policy.isRegistrationOpen(exhibition));
    }

    @Test
    @DisplayName("Should resolve target status according to priority rules")
    void testResolveTargetStatus() {
        // 1. today > endDate -> COMPLETED
        Exhibition exCompleted = Exhibition.builder()
                .status(ExhibitionStatus.ACTIVE)
                .startDate(LocalDate.of(2026, Month.JANUARY, 1))
                .endDate(LocalDate.of(2026, Month.JANUARY, 9))
                .build();
        assertEquals(ExhibitionStatus.COMPLETED,
                policy.resolveTargetStatus(exCompleted, LocalDate.of(2026, Month.JANUARY, 10)));

        // 2. today >= startDate -> ACTIVE
        Exhibition exActive = Exhibition.builder()
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.of(2026, Month.JANUARY, 10))
                .endDate(LocalDate.of(2026, Month.JANUARY, 15))
                .build();
        assertEquals(ExhibitionStatus.ACTIVE,
                policy.resolveTargetStatus(exActive, LocalDate.of(2026, Month.JANUARY, 10)));

        // 3. status == REGISTRATION and today >= startDate - 31 days -> PUBLISHED
        Exhibition exPublished = Exhibition.builder()
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.FEBRUARY, 10))
                .endDate(LocalDate.of(2026, Month.FEBRUARY, 25))
                .build();
        assertEquals(ExhibitionStatus.PUBLISHED,
                policy.resolveTargetStatus(exPublished, LocalDate.of(2026, Month.JANUARY, 10)));

        // 4. Otherwise -> null (no transition)
        // REGISTRATION before T-31
        Exhibition exRegEarly = Exhibition.builder()
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.MARCH, 1))
                .endDate(LocalDate.of(2026, Month.MARCH, 25))
                .build();
        assertNull(policy.resolveTargetStatus(exRegEarly, LocalDate.of(2026, Month.JANUARY, 10)));

        // PUBLISHED before T0
        Exhibition exPubEarly = Exhibition.builder()
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.of(2026, Month.JANUARY, 15))
                .endDate(LocalDate.of(2026, Month.JANUARY, 20))
                .build();
        assertNull(policy.resolveTargetStatus(exPubEarly, LocalDate.of(2026, Month.JANUARY, 10)));
    }

    @Test
    void coversNullAndDeadlineEdges() {
        assertNull(policy.getBoothReviewDeadline(null));
        assertNull(policy.getBoothReviewDeadline(Exhibition.builder().build()));
        assertFalse(policy.isBoothPreparationOpen(null));
        assertFalse(policy.isRegistrationOpen(null));
        assertFalse(policy.hasMinimumLeadTime(null, 1));

        LocalDate today = LocalDate.of(2026, Month.JANUARY, 10);
        assertNull(policy.resolveTargetStatus(null, today));
        assertNull(policy.resolveTargetStatus(Exhibition.builder().endDate(today).build(), today));
        assertNull(policy.resolveTargetStatus(Exhibition.builder().startDate(today).build(), today));
    }
}
