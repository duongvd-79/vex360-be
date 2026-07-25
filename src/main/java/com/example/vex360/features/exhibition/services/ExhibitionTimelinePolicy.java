package com.example.vex360.features.exhibition.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.shared.enums.ExhibitionStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExhibitionTimelinePolicy {
    public static final int DEFAULT_MINIMUM_LEAD_DAYS = 7;
    public static final int BOOTH_DEADLINE_DAYS_BEFORE_START = 3;

    private final Clock clock;

    public LocalDate getBoothReviewDeadline(Exhibition exhibition) {
        if (exhibition == null || exhibition.getStartDate() == null) {
            return null;
        }
        return exhibition.getStartDate().minusDays(BOOTH_DEADLINE_DAYS_BEFORE_START);
    }

    public boolean isBoothPreparationOpen(Exhibition exhibition) {
        LocalDate deadline = getBoothReviewDeadline(exhibition);
        if (deadline == null) {
            return false;
        }
        return !LocalDate.now(clock).isAfter(deadline);
    }

    public boolean isRegistrationOpen(Exhibition exhibition) {
        if (exhibition == null || exhibition.getStatus() != ExhibitionStatus.REGISTRATION) {
            return false;
        }
        return isBoothPreparationOpen(exhibition);
    }

    public long getDaysUntilDeadline(Exhibition exhibition) {
        LocalDate deadline = getBoothReviewDeadline(exhibition);
        if (deadline == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(clock), deadline);
    }

    public boolean hasMinimumLeadTime(LocalDate startDate) {
        return hasMinimumLeadTime(startDate, DEFAULT_MINIMUM_LEAD_DAYS);
    }

    public boolean hasMinimumLeadTime(LocalDate startDate, int minDays) {
        if (startDate == null) {
            return false;
        }
        LocalDate minStartDate = LocalDate.now(clock).plusDays(minDays);
        return !startDate.isBefore(minStartDate);
    }
}
