package com.example.vex360.features.company.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateStoragePackageOrderRequest {
    @NotNull(message = "ID gói lưu trữ không được để trống.")
    @Positive(message = "ID gói lưu trữ phải lớn hơn 0.")
    Integer packageId;
}
