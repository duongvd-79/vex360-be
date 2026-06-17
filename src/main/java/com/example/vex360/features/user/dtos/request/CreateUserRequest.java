package com.example.vex360.features.user.dtos.request;

import com.example.vex360.shared.enums.Role;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải tối thiểu 8 ký tự")
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @NotNull(message = "Role không được để trống")
    private Role role;
    private String avatarUrl;
}
