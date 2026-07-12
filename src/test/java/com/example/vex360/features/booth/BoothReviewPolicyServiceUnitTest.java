package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothReviewPolicyServiceUnitTest {
    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    private BoothReviewPolicyService policyService;
    private Booth booth;

    @BeforeEach
    void setup() {
        policyService = new BoothReviewPolicyService(boothReviewRequestRepository, panoramaRepository);
        booth = booth(LocalDate.now().plusDays(10));
    }

    @Test
    void assertEditableRejectsPublishedBooth() {
        booth.setStatus(BoothStatus.PUBLISHED);

        AppException exception = assertThrows(AppException.class, () -> policyService.assertEditable(booth));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
    }

    @Test
    void assertBeforeReviewDeadlineRejectsWhenWithinThreeDays() {
        Booth deadlineBooth = booth(LocalDate.now().plusDays(3));

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
