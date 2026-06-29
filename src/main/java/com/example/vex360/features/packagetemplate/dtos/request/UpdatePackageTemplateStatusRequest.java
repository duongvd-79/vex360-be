package com.example.vex360.features.packagetemplate.dtos.request;

import com.example.vex360.shared.enums.PackageTemplateStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePackageTemplateStatusRequest {
    @NotNull(message = "Trạng thái gói không được để trống")
    private PackageTemplateStatus status;
}
