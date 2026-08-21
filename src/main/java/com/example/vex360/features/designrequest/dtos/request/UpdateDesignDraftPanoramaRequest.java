package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDesignDraftPanoramaRequest {
    @Pattern(regexp = "^(?=.*\\S).+$", message = "Tên panorama không được để trống nếu được cung cấp.")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự.")
    private String name;
    private UUID panoramaAssetId;
    private Boolean isDefault;
}
