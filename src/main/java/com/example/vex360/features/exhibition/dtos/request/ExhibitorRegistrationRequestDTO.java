package com.example.vex360.features.exhibition.dtos.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitorRegistrationRequestDTO {
    private UUID exhibitionUuid;

    @Deprecated
    @Positive(message = "ID gói triển lãm phải lớn hơn 0.")
    private Integer exhibitionPackageId;

    @NotBlank(message = "Lý do tham gia không được để trống")
    @Size(max = 2000, message = "Lý do tham gia không được vượt quá 2000 ký tự")
    private String participationReason;

    @NotBlank(message = "Tên gian hàng không được để trống")
    @Size(max = 255, message = "Tên gian hàng không được vượt quá 255 ký tự")
    private String boothName;

    @NotBlank(message = "Mô tả gian hàng không được để trống")
    @Size(max = 2000, message = "Mô tả gian hàng không được vượt quá 2000 ký tự")
    private String boothDescription;

    @JsonIgnore
    @AssertTrue(message = "UUID triển lãm hoặc ID gói triển lãm không được để trống")
    public boolean isRegistrationTargetValid() {
        return exhibitionUuid != null || exhibitionPackageId != null;
    }
}
