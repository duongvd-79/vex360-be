package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.Valid;
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
public class SubmitDesignDraftPanoramaRequest {
    @NotBlank(message = "Client key không được để trống")
    @Size(max = 100, message = "Client key không được vượt quá 100 ký tự.")
    private String clientKey;

    @NotBlank(message = "Tên panorama không được để trống")
    @Size(max = 255, message = "Tên panorama không được vượt quá 255 ký tự.")
    private String name;

    @NotBlank(message = "Image url không được để trống")
    @URL(message = "Image URL không hợp lệ.")
    private String imageUrl;

    @Size(max = 500, message = "Image key không được vượt quá 500 ký tự.")
    private String imageKey;

    @NotNull(message = "Thứ tự panorama không được để trống")
    @PositiveOrZero(message = "Thứ tự panorama phải lớn hơn hoặc bằng 0.")
    private Integer orderIndex;

    private Boolean isDefault;

    @Valid
    private List<SubmitDesignDraftHotspotRequest> hotspots;
}
