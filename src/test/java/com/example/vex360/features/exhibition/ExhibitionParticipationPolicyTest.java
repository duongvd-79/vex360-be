package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

class ExhibitionParticipationPolicyTest {

    private final ExhibitionParticipationPolicy policy = new ExhibitionParticipationPolicy();

    @Test
    void supportsParticipationOnlyForWithBooths() {
        assertTrue(policy.supportsParticipation(ExhibitionExperienceMode.WITH_BOOTHS));
        assertFalse(policy.supportsParticipation(ExhibitionExperienceMode.STANDALONE));
        assertFalse(policy.supportsParticipation((Exhibition) null));
    }

    @Test
    void standaloneParticipationUsesDedicatedConflictError() {
        Exhibition exhibition = Exhibition.builder()
                .experienceMode(ExhibitionExperienceMode.STANDALONE)
                .build();

        AppException exception = assertThrows(AppException.class,
                () -> policy.assertSupportsParticipation(exhibition));

        assertEquals(ErrorCode.EXHIBITION_PARTICIPATION_NOT_SUPPORTED, exception.getErrorCode());
    }
}
