package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.vex360.features.exhibition.dtos.request.ExhibitorRegistrationRequestDTO;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ExhibitorRegistrationRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequest_HasNoViolations() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void boothName_Missing_FailsValidation(String boothName) {
        ExhibitorRegistrationRequestDTO dto = validRequest();
        dto.setBoothName(boothName);
        assertInvalid(dto, "boothName");
    }

    @Test
    void maximumLengths_Succeed() {
        ExhibitorRegistrationRequestDTO dto = validRequest();
        dto.setBoothName("A".repeat(255));
        dto.setBoothDescription("B".repeat(2000));
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void boothName_256Chars_FailsValidation() {
        ExhibitorRegistrationRequestDTO dto = validRequest();
        dto.setBoothName("A".repeat(256));
        assertInvalid(dto, "boothName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void boothDescription_Missing_FailsValidation(String boothDescription) {
        ExhibitorRegistrationRequestDTO dto = validRequest();
        dto.setBoothDescription(boothDescription);
        assertInvalid(dto, "boothDescription");
    }

    @Test
    void boothDescription_2001Chars_FailsValidation() {
        ExhibitorRegistrationRequestDTO dto = validRequest();
        dto.setBoothDescription("B".repeat(2001));
        assertInvalid(dto, "boothDescription");
    }

    private ExhibitorRegistrationRequestDTO validRequest() {
        ExhibitorRegistrationRequestDTO dto = new ExhibitorRegistrationRequestDTO();
        dto.setExhibitionPackageId(10);
        dto.setParticipationReason("Valid reason");
        dto.setBoothName("Valid Booth");
        dto.setBoothDescription("Valid Booth Description");
        return dto;
    }

    private void assertInvalid(ExhibitorRegistrationRequestDTO dto, String property) {
        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(property)));
    }
}
