package com.example.vex360.features.designrequest.dtos.response;

import com.example.vex360.features.designrequest.enums.DesignRequestScope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignRequestScopeAvailabilityDTO {
    private DesignRequestScope scope;
    private boolean available;
    private String reasonCode;
}
