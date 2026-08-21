package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDesignDraftPanoramaRequest {
    @NotBlank(message = "Ten panorama khong duoc de trong")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự.")
    private String name;

    @NotNull(message = "Panorama asset khong duoc de trong")
    private UUID panoramaAssetId;

    @PositiveOrZero(message = "Thứ tự panorama phải lớn hơn hoặc bằng 0.")
    private Integer orderIndex;
    private Boolean isDefault;
}
