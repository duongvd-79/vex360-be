package com.example.vex360.features.auth.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(max = 72, message = "Mật khẩu không được vượt quá 72 ký tự.")
    private String password;

    private boolean rememberMe;
}
