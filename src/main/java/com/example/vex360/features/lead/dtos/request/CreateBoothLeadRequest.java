package com.example.vex360.features.lead.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBoothLeadRequest(
        @NotBlank(message = "Vui lòng nhập họ tên.")
        @Size(min = 2, max = 120, message = "Họ tên phải có từ 2 đến 120 ký tự.")
        @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ '\\-][\\p{L}\\p{M}]+)*$",
                message = "Họ tên chỉ được chứa chữ cái, khoảng trắng, dấu nháy đơn hoặc dấu gạch nối.")
        String fullName,

        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 255, message = "Email không được vượt quá 255 ký tự.")
        String email,

        @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
        String phoneNumber,

        @NotBlank(message = "Vui lòng nhập tên công ty hoặc tổ chức.")
        @Size(max = 180, message = "Tên công ty không được vượt quá 180 ký tự.")
        String companyName,

        @Size(max = 1500, message = "Nội dung quan tâm không được vượt quá 1.500 ký tự.")
        String message,

        @AssertTrue(message = "Bạn cần đồng ý chia sẻ thông tin để gửi yêu cầu.")
        boolean consent) {
}
