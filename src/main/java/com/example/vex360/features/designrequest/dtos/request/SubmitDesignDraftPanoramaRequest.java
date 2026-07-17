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
    @NotBlank(message = "Client key khong duoc de trong")
    private String clientKey;

    @NotBlank(message = "Ten panorama khong duoc de trong")
    private String name;

    @NotBlank(message = "Image url khong duoc de trong")
    private String imageUrl;

    private String imageKey;

    @NotNull(message = "Thu tu panorama khong duoc de trong")
    private Integer orderIndex;

    private Boolean isDefault;

    @Valid
    private List<SubmitDesignDraftHotspotRequest> hotspots;
}
