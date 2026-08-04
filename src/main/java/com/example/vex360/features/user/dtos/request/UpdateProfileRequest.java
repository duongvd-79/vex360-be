package com.example.vex360.features.user.dtos.request;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 120, message = "Họ và tên phải có từ 2 đến 120 ký tự.")
    @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ '\\-][\\p{L}\\p{M}]+)*$",
            message = "Họ và tên chỉ được chứa chữ cái, khoảng trắng, dấu nháy đơn hoặc dấu gạch nối.")
    private String fullName;

    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @URL(message = "URL ảnh đại diện không hợp lệ.")
    private String avatarUrl;
}
