package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDesignDraftPanoramaRequest {
    private String name;
    private UUID panoramaAssetId;
    private Boolean isDefault;
}
