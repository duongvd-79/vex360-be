package com.example.vex360.features.partnership.dtos.response;

import com.example.vex360.shared.enums.PartnershipVerificationResult;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PartnershipVerificationResponseDTO {
    private PartnershipVerificationResult result;
}
