package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;

@ExtendWith(MockitoExtension.class)
class BoothReviewPolicyServiceUnitTest {
    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    private BoothReviewPolicyService policyService;
    private Booth booth;
    private Clock clock;

    @BeforeEach
    void setup() {
        clock = Clock.fixed(Instant.parse("2026-01-10T08:00:00Z"), ZoneOffset.UTC);
        ExhibitionTimelinePolicy timelinePolicy = new ExhibitionTimelinePolicy(clock);
        policyService = new BoothReviewPolicyService(boothReviewRequestRepository, panoramaRepository, timelinePolicy);
        booth = booth(LocalDate.now(clock).plusDays(10));
    }

    @ParameterizedTest
    @EnumSource(value = BoothStatus.class, names = { "DESIGNING", "PENDING", "PUBLISHED", "ARCHIVED" })
    void assertEditableRejectsEveryNonDraftStatus(BoothStatus status) {
        booth.setStatus(status);

        AppException exception = assertThrows(AppException.class, () -> policyService.assertEditable(booth));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
    }

    @Test
    void assertEditableAllowsDraftRegardlessOfReviewHistory() {
        assertDoesNotThrow(() -> policyService.assertEditable(booth));
    }

    @Test
    void assertCanSubmitReviewRejectsDesigningBooth() {
        booth.setStatus(BoothStatus.DESIGNING);

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertCanSubmitReview(booth));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
    }

    @Test
    void assertBeforeReviewDeadlineRejectsWhenWithinThreeDays() {
        Booth deadlineBooth = booth(LocalDate.now(clock).plusDays(2));

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertBeforeReviewDeadline(deadlineBooth));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    @Test
    void assertCanSubmitReviewAllowsDraftBoothBeforeDeadlineWithPanorama() {
        when(boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING))
                .thenReturn(false);
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        assertDoesNotThrow(() -> policyService.assertCanSubmitReview(booth));
    }

    @Test
    void assertCanSubmitReviewAllowsRejectedBoothAfterDeadlineWhileRegistrationIsOpen() {
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.REGISTRATION);
        BoothReviewRequest rejectedReview = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.REJECTED)
                .build();
        when(boothReviewRequestRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.of(rejectedReview));
        when(boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING))
                .thenReturn(false);
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        assertDoesNotThrow(() -> policyService.assertCanSubmitReview(booth));
    }

    @Test
    void assertCanSubmitReviewRejectsFirstSubmissionAfterDeadline() {
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.REGISTRATION);
        when(boothReviewRequestRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertCanSubmitReview(booth));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    @Test
    void assertCanSubmitReviewAllowsRejectedBoothAfterDeadlineWhileExhibitionIsPublished() {
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.PUBLISHED);
        BoothReviewRequest rejectedReview = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.REJECTED)
                .build();
        when(boothReviewRequestRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.of(rejectedReview));
        when(boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING))
                .thenReturn(false);
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        assertDoesNotThrow(() -> policyService.assertCanSubmitReview(booth));
    }

    @Test
    void assertCanSubmitReviewRejectsRejectedBoothAfterExhibitionIsActive() {
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.ACTIVE);
        BoothReviewRequest rejectedReview = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.REJECTED)
                .build();
        Mockito.lenient()
                .when(boothReviewRequestRepository
                        .findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.of(rejectedReview));

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertCanSubmitReview(booth));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    @Test
    void assertCanSubmitReviewAllowsCompletedDesignRequestLateEditException() {
        // Today is Jan 10, start date is Jan 12 (T-2). lateEditAllowedUntil is set to
        // Jan 11 (T-1)
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.REGISTRATION);
        booth.setLateEditAllowedUntil(LocalDate.now(clock).plusDays(1)); // T-1

        when(boothReviewRequestRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.empty());
        when(boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING))
                .thenReturn(false);
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        assertDoesNotThrow(() -> policyService.assertCanSubmitReview(booth));
    }

    @Test
    void assertBeforeReviewDeadlineRejectsAtT0EvenWithLateEditAllowedUntil() {
        // Today is Jan 10, start date is Jan 10 (T0).
        booth = booth(LocalDate.now(clock));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.REGISTRATION);
        booth.setLateEditAllowedUntil(LocalDate.now(clock).plusDays(1));

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertBeforeReviewDeadline(booth));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    @Test
    void assertCanStartEditRules() {
        // Status must be PUBLISHED
        booth.setStatus(BoothStatus.PUBLISHED);
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStatus(ExhibitionStatus.REGISTRATION);

        // Allowed at T-3 (startDate = today + 3 days)
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStartDate(LocalDate.now(clock).plusDays(3));
        assertDoesNotThrow(() -> policyService.assertCanStartEdit(booth));

        // Rejected at T-2 (startDate = today + 2 days)
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition()
                .setStartDate(LocalDate.now(clock).plusDays(2));
        AppException ex = assertThrows(AppException.class, () -> policyService.assertCanStartEdit(booth));
        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, ex.getErrorCode());
    }

    @Test
    void warnAndBanPolicyRules() {
        // Today is Jan 10
        // Case 1: T-5 (startDate = Jan 15), warningCount = 0 -> Warn allowed, Ban
        // allowed
        booth = booth(LocalDate.now(clock).plusDays(5));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().setStatus(ExhibitionStatus.PUBLISHED);
        booth.setStatus(BoothStatus.PUBLISHED);
        booth.setWarningCount(0);

        assertTrue(policyService.isBoothWarnAllowed(booth));
        assertTrue(policyService.isBoothBanAllowed(booth));
        assertDoesNotThrow(() -> policyService.assertCanWarnBooth(booth));
        assertDoesNotThrow(() -> policyService.assertCanBanBooth(booth));

        // Case 2: T-3 (startDate = Jan 13) -> Warn allowed at T-3
        booth = booth(LocalDate.now(clock).plusDays(3));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().setStatus(ExhibitionStatus.PUBLISHED);
        booth.setStatus(BoothStatus.PUBLISHED);
        booth.setWarningCount(0);

        assertTrue(policyService.isBoothWarnAllowed(booth));

        // Case 3: T-2 (startDate = Jan 12) -> Warn NOT allowed at T-2, Ban IS allowed
        booth = booth(LocalDate.now(clock).plusDays(2));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().setStatus(ExhibitionStatus.PUBLISHED);
        booth.setStatus(BoothStatus.PUBLISHED);
        booth.setWarningCount(0);

        assertFalse(policyService.isBoothWarnAllowed(booth));
        assertTrue(policyService.isBoothBanAllowed(booth));
        AppException exWarn = assertThrows(AppException.class, () -> policyService.assertCanWarnBooth(booth));
        assertSame(ErrorCode.BOOTH_WARNING_NOT_ALLOWED_AFTER_DEADLINE, exWarn.getErrorCode());
        assertDoesNotThrow(() -> policyService.assertCanBanBooth(booth));

        // Case 4: warningCount = 1 at T-5 -> Warn NOT allowed (only 1 warning allowed)
        booth = booth(LocalDate.now(clock).plusDays(5));
        booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().setStatus(ExhibitionStatus.PUBLISHED);
        booth.setStatus(BoothStatus.PUBLISHED);
        booth.setWarningCount(1);

        assertFalse(policyService.isBoothWarnAllowed(booth));
        AppException exWarnTwice = assertThrows(AppException.class, () -> policyService.assertCanWarnBooth(booth));
        assertSame(ErrorCode.BOOTH_ALREADY_WARNED, exWarnTwice.getErrorCode());

        // Case 5: Already BANNED -> Warn & Ban NOT allowed
        booth.setStatus(BoothStatus.BANNED);
        assertFalse(policyService.isBoothWarnAllowed(booth));
        assertFalse(policyService.isBoothBanAllowed(booth));
        AppException exAlreadyBanned = assertThrows(AppException.class, () -> policyService.assertCanBanBooth(booth));
        assertSame(ErrorCode.BOOTH_ALREADY_BANNED, exAlreadyBanned.getErrorCode());
    }

    private Booth booth(LocalDate startDate) {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .name("Expo")
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(startDate)
                .endDate(startDate.plusDays(2))
                .build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .id(1)
                .exhibition(exhibition)
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .exhibitionPackage(exhibitionPackage)
                .build();
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .exhibitorRegistration(registration)
                .build();
    }
}
