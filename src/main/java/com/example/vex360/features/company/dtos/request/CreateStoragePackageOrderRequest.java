package com.example.vex360.features.company.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStoragePackageOrderRequest {
    @NotNull
    Integer packageId;
}
