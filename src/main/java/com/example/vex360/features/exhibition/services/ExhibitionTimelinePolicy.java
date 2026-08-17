package com.example.vex360.features.exhibition.services;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.shared.enums.ExhibitionStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExhibitionTimelinePolicy {
    public static final int DEFAULT_MINIMUM_LEAD_DAYS = 31;
    public static final int BOOTH_DEADLINE_DAYS_BEFORE_START = 3;

    private final Clock clock;

    public LocalDate getBoothReviewDeadline(Exhibition exhibition) {
        if (exhibition == null || exhibition.getStartDate() == null) {
            return null;
        }
        return exhibition.getStartDate().minusDays(BOOTH_DEADLINE_DAYS_BEFORE_START);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ExhibitionStatus resolveTargetStatus(Exhibition exhibition, LocalDate today) {
        if (exhibition == null || exhibition.getStartDate() == null || exhibition.getEndDate() == null) {
            return null;
        }
        if (today.isAfter(exhibition.getEndDate())) {
            return ExhibitionStatus.COMPLETED;
        }
        if (!today.isBefore(exhibition.getStartDate())) {
            return ExhibitionStatus.ACTIVE;
        }
        if (exhibition.getStatus() == ExhibitionStatus.REGISTRATION
                && !today.isBefore(exhibition.getStartDate().minusDays(DEFAULT_MINIMUM_LEAD_DAYS))) {
            return ExhibitionStatus.PUBLISHED;
        }
        return null;
    }

    public boolean isBoothPreparationOpen(Exhibition exhibition) {
        LocalDate deadline = getBoothReviewDeadline(exhibition);
        if (deadline == null) {
            return false;
        }
        return !today().isAfter(deadline);
    }

    public boolean isRegistrationOpen(Exhibition exhibition) {
        if (exhibition == null) {
            return false;
        }
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            return false;
        }
        return isBoothPreparationOpen(exhibition);
    }

    public boolean hasMinimumLeadTime(LocalDate startDate) {
        return hasMinimumLeadTime(startDate, DEFAULT_MINIMUM_LEAD_DAYS);
    }

    public boolean hasMinimumLeadTime(LocalDate startDate, int minDays) {
        if (startDate == null) {
            return false;
        }
        LocalDate minStartDate = today().plusDays(minDays);
        return !startDate.isBefore(minStartDate);
    }
}
