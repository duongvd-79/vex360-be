package com.example.vex360.features.booth.services;

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
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothReviewPolicyService {
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final PanoramaRepository panoramaRepository;
    private final ExhibitionTimelinePolicy exhibitionTimelinePolicy;

    public boolean isBoothEditAllowed(Booth booth) {
        if (booth.getIsTemplate() != null && booth.getIsTemplate()) {
            return true;
        }
        Exhibition exhibition = getExhibition(booth);
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            return false;
        }

        LocalDate today = exhibitionTimelinePolicy.today();
        LocalDate startDate = exhibition.getStartDate();
        if (startDate == null) {
            return false;
        }

        if (!today.isBefore(startDate)) {
            return false;
        }

        LocalDate t3Deadline = startDate.minusDays(3);
        if (!today.isAfter(t3Deadline)) {
            return true;
        }

        LocalDate t1Deadline = startDate.minusDays(1);
        if (today.isAfter(t1Deadline)) {
            return false;
        }

        boolean latestReviewRejected = boothReviewRequestRepository
                .findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId())
                .map(review -> review.getStatus() == BoothReviewStatus.REJECTED)
                .orElse(false);
        if (latestReviewRejected) {
            return true;
        }

        return (booth.getLateEditAllowedUntil() != null && !today.isAfter(booth.getLateEditAllowedUntil()));
    }

    public void assertEditable(Booth booth) {
        if (booth.getStatus() != BoothStatus.DRAFT) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        assertBeforeReviewDeadline(booth);
    }

    public void assertMetadataEditable(Booth booth) {
        if (booth.getStatus() != BoothStatus.DRAFT
                && booth.getStatus() != BoothStatus.DESIGN_REQUEST_PENDING) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        assertBeforeReviewDeadline(booth);
    }

    public void assertBeforeReviewDeadline(Booth booth) {
        if (!isBoothEditAllowed(booth)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void assertCanStartEdit(Booth booth) {
        if (booth.getStatus() != BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        if (booth.getIsTemplate() != null && booth.getIsTemplate()) {
            return;
        }
        Exhibition exhibition = getExhibition(booth);
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
        LocalDate today = exhibitionTimelinePolicy.today();
        LocalDate startDate = exhibition.getStartDate();
        if (startDate == null || today.isAfter(startDate.minusDays(3))) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void assertCanReviewBooth(Booth booth) {
        if (booth.getIsTemplate() != null && booth.getIsTemplate()) {
            return;
        }
        Exhibition exhibition = getExhibition(booth);
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
        LocalDate today = exhibitionTimelinePolicy.today();
        LocalDate startDate = exhibition.getStartDate();
        if (startDate == null || !today.isBefore(startDate)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void assertCanSubmitReview(Booth booth) {
        assertEditable(booth);
        if (boothReviewRequestRepository.existsByBoothIdAndStatus(booth.getId(), BoothReviewStatus.PENDING)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_ALREADY_PENDING);
        }
        if (booth.getName() == null || booth.getName().isBlank()
                || panoramaRepository.countByBoothId(booth.getId()) == 0) {
            throw new AppException(ErrorCode.BOOTH_DRAFT_NOT_REVIEWABLE);
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
            throw new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        if (exhibitionPackage == null) {
            throw new AppException(ErrorCode.REGISTRATION_PACKAGE_MISSING);
        }
        if (exhibitionPackage.getExhibition() == null) {
            throw new AppException(ErrorCode.REGISTRATION_EXHIBITION_MISSING);
        }
        return exhibitionPackage.getExhibition();
    }
}
