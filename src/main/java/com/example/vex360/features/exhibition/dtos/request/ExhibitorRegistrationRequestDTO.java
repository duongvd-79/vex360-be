package com.example.vex360.features.exhibition.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitorRegistrationRequestDTO {
    @NotNull(message = "Exhibition package ID is required")
    private Integer exhibitionPackageId;
}
