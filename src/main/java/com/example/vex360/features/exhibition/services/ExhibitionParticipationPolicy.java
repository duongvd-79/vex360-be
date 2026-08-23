package com.example.vex360.features.exhibition.services;

import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@Component
public class ExhibitionParticipationPolicy {

    public boolean supportsParticipation(Exhibition exhibition) {
        return exhibition != null && supportsParticipation(exhibition.getExperienceMode());
    }

    public boolean supportsParticipation(ExhibitionExperienceMode experienceMode) {
        return experienceMode == ExhibitionExperienceMode.WITH_BOOTHS;
    }

    public void assertSupportsParticipation(Exhibition exhibition) {
        if (!supportsParticipation(exhibition)) {
            throw new AppException(ErrorCode.EXHIBITION_PARTICIPATION_NOT_SUPPORTED);
        }
    }

    public void assertSupportsParticipation(ExhibitionExperienceMode experienceMode) {
        if (!supportsParticipation(experienceMode)) {
            throw new AppException(ErrorCode.EXHIBITION_PARTICIPATION_NOT_SUPPORTED);
        }
    }
}
