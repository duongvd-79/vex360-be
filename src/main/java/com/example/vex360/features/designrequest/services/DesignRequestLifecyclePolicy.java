package com.example.vex360.features.designrequest.services;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignRequestLifecyclePolicy {

    private final ExhibitionTimelinePolicy exhibitionTimelinePolicy;

    public void assertCanCreate(Booth booth) {
        if (booth.getIsTemplate() != null && booth.getIsTemplate()) {
            return;
        }
        Exhibition exhibition = getExhibition(booth);
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }

        LocalDate today = exhibitionTimelinePolicy.today();
        LocalDate startDate = exhibition.getStartDate();
        if (startDate == null || today.isAfter(startDate.minusDays(3))) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void assertCanContinue(DesignRequest designRequest) {
        if (designRequest == null || designRequest.getBooth() == null) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND);
        }
        Booth booth = designRequest.getBooth();
        if (booth.getIsTemplate() != null && booth.getIsTemplate()) {
            return;
        }
        Exhibition exhibition = getExhibition(booth);
        ExhibitionStatus status = exhibition.getStatus();
        if (status != ExhibitionStatus.REGISTRATION && status != ExhibitionStatus.PUBLISHED) {
            throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
        }

        LocalDate today = exhibitionTimelinePolicy.today();
        LocalDate startDate = exhibition.getStartDate();
        if (startDate == null || !today.isBefore(startDate)) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED);
        }
    }

    public void grantLateEditWindowIfEligible(DesignRequest designRequest) {
        if (designRequest == null || designRequest.getBooth() == null || designRequest.getCreatedAt() == null) {
            return;
        }
        Exhibition exhibition = getExhibition(designRequest.getBooth());
        if (exhibition.getStartDate() == null) {
            return;
        }
        LocalDate createdDate = LocalDate.ofInstant(designRequest.getCreatedAt(), ZoneOffset.UTC);
        if (!createdDate.isAfter(exhibition.getStartDate().minusDays(3))) {
            designRequest.getBooth().setLateEditAllowedUntil(exhibition.getStartDate().minusDays(1));
        }
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
