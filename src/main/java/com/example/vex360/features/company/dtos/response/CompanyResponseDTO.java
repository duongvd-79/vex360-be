package com.example.vex360.features.company.dtos.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin công ty thuộc sở hữu của một user")
public class CompanyResponseDTO {
    @Schema(description = "ID công ty", example = "7b2f8d56-0df0-4e10-9bb8-1eec3525e6f2")
    private UUID id;

    @Schema(description = "ID user sở hữu công ty", example = "0a11c6a6-65a5-4e37-8c5d-25091f25c4dc")
    private UUID ownerUserId;

    @Schema(description = "Tên công ty hoặc tổ chức", example = "Vex360 Partner")
    private String name;

    @Schema(description = "Ngành nghề hoạt động", example = "Technology")
    private String industry;

    @Schema(description = "Mô tả công ty", example = "Vex360 Partner builds immersive virtual exhibition experiences.")
    private String description;

    @Schema(description = "URL logo công ty", example = "https://cdn.example.com/logos/vex360-partner.png")
    private String logoUrl;

    @Schema(description = "Website chính thức", example = "https://partner.example.com")
    private String website;

    @Schema(description = "Email liên hệ của công ty", example = "partner@example.com")
    private String email;

    @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
    private String phone;

    @Schema(description = "Địa chỉ liên hệ", example = "123 Nguyen Hue, District 1, Ho Chi Minh City")
    private String address;

    @Schema(
            description = "Trạng thái hồ sơ công ty",
            example = "INCOMPLETE_PROFILE",
            allowableValues = {"INCOMPLETE_PROFILE", "ACTIVE", "ARCHIVED"})
    private String status;
}
