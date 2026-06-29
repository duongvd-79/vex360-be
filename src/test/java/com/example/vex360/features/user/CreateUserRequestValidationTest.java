package com.example.vex360.features.user;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.shared.enums.Role;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateUserRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validPhoneNumberPassesValidation() {
        CreateUserRequest request = validRequest("0912345678");

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void nullPhoneNumberFailsValidation() {
        CreateUserRequest request = validRequest(null);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(violation -> "phoneNumber".equals(violation.getPropertyPath().toString())));
    }

    @Test
    void blankPhoneNumberFailsValidation() {
        CreateUserRequest request = validRequest("");

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(violation -> "phoneNumber".equals(violation.getPropertyPath().toString())));
    }

    @Test
    void invalidPhoneNumberFailsValidation() {
        CreateUserRequest request = validRequest("123");

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(violation -> "phoneNumber".equals(violation.getPropertyPath().toString())));
    }

    private CreateUserRequest validRequest(String phoneNumber) {
        return new CreateUserRequest(
                "user@example.com",
                "User Name",
                phoneNumber,
                Role.VISITOR);
    }
}
