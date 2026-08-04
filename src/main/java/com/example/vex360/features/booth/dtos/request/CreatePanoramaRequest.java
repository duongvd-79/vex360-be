package com.example.vex360.features.booth.dtos.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePanoramaRequest {
    @NotBlank(message = "Client key cua panorama khong duoc de trong")
    @Size(max = 100, message = "Client key không được vượt quá 100 ký tự.")
    private String clientKey;

    @NotBlank(message = "File key cua panorama khong duoc de trong")
    @Size(max = 500, message = "File key không được vượt quá 500 ký tự.")
    private String fileKey;

    @NotBlank(message = "Ten panorama khong duoc de trong")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự.")
    private String name;

    @PositiveOrZero(message = "Thứ tự panorama phải lớn hơn hoặc bằng 0.")
    private Integer orderIndex;

    private Boolean isDefault;

    private List<@Valid CreateHotspotRequest> hotspots;
}
