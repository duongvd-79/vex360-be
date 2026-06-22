package com.example.vex360.features.company.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin dùng để hoàn thiện hồ sơ công ty sau khi partnership request được duyệt")
public class UpdateCompanyProfileRequest {
    @Schema(description = "Ngành nghề hoạt động của công ty", example = "Technology")
    private String industry;

    @Schema(description = "Mô tả ngắn về công ty", example = "Vex360 Partner builds immersive virtual exhibition experiences.")
    private String description;

    @Schema(description = "URL logo công ty", example = "https://cdn.example.com/logos/vex360-partner.png")
    private String logoUrl;

    @Schema(description = "Website chính thức của công ty", example = "https://partner.example.com")
    private String website;

    @Schema(description = "Số điện thoại liên hệ của công ty", example = "0912345678")
    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Schema(description = "Địa chỉ liên hệ của công ty", example = "123 Nguyen Hue, District 1, Ho Chi Minh City")
    private String address;
}
