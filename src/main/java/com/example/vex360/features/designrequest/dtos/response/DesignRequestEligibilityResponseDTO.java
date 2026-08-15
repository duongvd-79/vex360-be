package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignRequestMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignRequestEligibilityResponseDTO {
    private UUID boothId;
    private DesignRequestMode mode;
    private boolean eligible;
    private String reasonCode;
    private Integer remainingDesignActions;
}
