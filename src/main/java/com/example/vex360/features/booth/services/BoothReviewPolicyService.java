package com.example.vex360.features.booth.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothReviewPolicyService {
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final PanoramaRepository panoramaRepository;
    private final Clock clock;

    public void assertEditable(Booth booth) {
        if (booth.getStatus() != BoothStatus.DRAFT) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
    }

    public void assertBeforeReviewDeadline(Booth booth) {
        Exhibition exhibition = getExhibition(booth);
        LocalDate deadline = exhibition.getStartDate().minusDays(3);
        if (!LocalDate.now(clock).isBefore(deadline)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void assertCanSubmitReview(Booth booth) {
        assertEditable(booth);
        assertBeforeReviewDeadline(booth);
        if (boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_ALREADY_PENDING);
        }
        if (booth.getName() == null || booth.getName().isBlank()
                || panoramaRepository.countByBoothId(booth.getId()) == 0) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
    }

    @Transactional(readOnly = true)
    public BoothReviewRequest getOrganizerReviewRequest(User organizer, UUID exhibitionUuid, UUID requestId) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return boothReviewRequestRepository.findForOrganizer(organizer.getId(), exhibitionUuid, requestId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_REVIEW_REQUEST_NOT_FOUND));
    }

    private Exhibition getExhibition(Booth booth) {
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        if (exhibitionPackage == null || exhibitionPackage.getExhibition() == null) {
            throw new AppException(ErrorCode.INVALID_BOOTH);
        }
        return exhibitionPackage.getExhibition();
    }
}
