package com.example.vex360.features.user.dtos.request;

import com.example.vex360.shared.enums.Role;
import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
    private String email;

    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự.")
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 120, message = "Họ và tên phải có từ 2 đến 120 ký tự.")
    @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ '\\-][\\p{L}\\p{M}]+)*$",
            message = "Họ và tên chỉ được chứa chữ cái, khoảng trắng, dấu nháy đơn hoặc dấu gạch nối.")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @NotNull(message = "Role không được để trống")
    private Role role;

    @URL(message = "URL ảnh đại diện không hợp lệ.")
    private String avatarUrl;
}
