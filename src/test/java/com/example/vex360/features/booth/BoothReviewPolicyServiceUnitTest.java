package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(boothReviewRequestRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.of(rejectedReview));

        AppException exception = assertThrows(
                AppException.class,
                () -> policyService.assertCanSubmitReview(booth));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    private Booth booth(LocalDate startDate) {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .name("Expo")
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
                .exhibitorRegistration(registration)
                .build();
    }
}
