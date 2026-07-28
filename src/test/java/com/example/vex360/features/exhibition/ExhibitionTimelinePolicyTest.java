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
    @DisplayName("Should validate minimum lead time of 7 days")
    void testHasMinimumLeadTime() {
        // Today is Jan 10 -> min start date is Jan 17
        assertTrue(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 17)));
        assertTrue(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 20)));
        assertFalse(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 16)));
        assertFalse(policy.hasMinimumLeadTime(LocalDate.of(2026, Month.JANUARY, 10)));
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
}
