package com.example.vex360.features.user.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    private String avatarUrl;
}
