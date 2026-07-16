package com.example.vex360.features.partnership.dtos.request;

import com.example.vex360.shared.enums.PartnershipVerificationAction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyPartnershipRequest {
    @NotBlank
    private String token;

    @NotNull
    private PartnershipVerificationAction action;
}
