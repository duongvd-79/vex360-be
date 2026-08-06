package com.example.vex360.features.company.dtos.request;

import org.hibernate.validator.constraints.URL;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin dùng để hoàn thiện hồ sơ công ty sau khi partnership request được duyệt")
public class UpdateCompanyProfileRequest {
    @Schema(description = "Ngành nghề hoạt động của công ty", example = "Technology")
    @NotBlank(message = "Industry must not be blank")
    @Size(max = 255, message = "Ngành nghề không được vượt quá 255 ký tự.")
    private String industry;

    @Schema(description = "Mô tả ngắn về công ty", example = "Vex360 Partner builds immersive virtual exhibition experiences.")
    @NotBlank(message = "Description must not be blank")
    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự.")
    private String description;

    @Schema(description = "URL logo công ty", example = "https://cdn.example.com/logos/vex360-partner.png")
    @URL(message = "URL logo không hợp lệ.")
    private String logoUrl;

    @Schema(description = "Website chính thức của công ty", example = "https://partner.example.com")
    @URL(message = "Website không hợp lệ.")
    private String website;

    @Schema(description = "Số điện thoại liên hệ của công ty", example = "0912345678")
    @NotBlank(message = "Requester phone number must not be blank")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại không hợp lệ!")
    private String phone;

    @Schema(description = "Địa chỉ liên hệ của công ty", example = "123 Nguyen Hue, District 1, Ho Chi Minh City")
    @NotBlank(message = "Address must not be blank")
    @Size(max = 1000, message = "Địa chỉ không được vượt quá 1000 ký tự.")
    private String address;
}
