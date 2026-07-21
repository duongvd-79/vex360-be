package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftPanoramaRequest {
    @NotBlank(message = "Client key không được để trống")
    private String clientKey;

    @NotBlank(message = "Tên panorama không được để trống")
    private String name;

    @NotBlank(message = "Image url không được để trống")
    private String imageUrl;

    private String imageKey;

    @NotNull(message = "Thứ tự panorama không được để trống")
    private Integer orderIndex;

    private Boolean isDefault;

    @Valid
    private List<SubmitDesignDraftHotspotRequest> hotspots;
}
