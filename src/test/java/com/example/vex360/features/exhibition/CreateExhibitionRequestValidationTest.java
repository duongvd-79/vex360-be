package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateExhibitionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEndDateBeforeStartDate() {
        CreateExhibitionRequest request = validRequest(
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(1));

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getMessage().equals(
                        "Ngày kết thúc phải sau ngày bắt đầu.")));
    }

    @Test
    void rejectsExhibitionLongerThanNinetyDays() {
        LocalDate startDate = LocalDate.now().plusDays(1);
        CreateExhibitionRequest request = validRequest(startDate, startDate.plusDays(91));

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getMessage().equals(
                        "Thời gian triển lãm không được vượt quá 90 ngày.")));
    }

    @Test
    void standaloneAcceptsZeroExpectedBooths() {
        CreateExhibitionRequest request = validRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2));
        request.setExperienceMode(ExhibitionExperienceMode.STANDALONE);
        request.setEstimatedBooths(0);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void withBoothsRejectsZeroExpectedBooths() {
        CreateExhibitionRequest request = validRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2));
        request.setExperienceMode(ExhibitionExperienceMode.WITH_BOOTHS);
        request.setEstimatedBooths(0);

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString()
                        .equals("estimatedBoothsValidForExperienceMode")));
    }

    @Test
    void defaultsToWithBooths() {
        CreateExhibitionRequest request = validRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2));

        assertEquals(ExhibitionExperienceMode.WITH_BOOTHS, request.getExperienceMode());
    }

    private CreateExhibitionRequest validRequest(LocalDate startDate, LocalDate endDate) {
        ConfigureExhibitionPackageRequest exhibitionPackage =
                ConfigureExhibitionPackageRequest.builder()
                        .templateId(UUID.randomUUID())
                        .finalPrice(BigDecimal.ZERO)
                        .build();

        return CreateExhibitionRequest.builder()
                .name("Triển lãm")
                .category("Công nghệ")
                .startDate(startDate)
                .endDate(endDate)
                .estimatedBooths(1)
                .packages(List.of(exhibitionPackage))
                .build();
    }
}
