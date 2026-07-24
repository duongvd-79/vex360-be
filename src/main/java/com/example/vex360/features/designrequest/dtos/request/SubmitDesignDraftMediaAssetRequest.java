package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftMediaAssetRequest {

    private UUID id;

    @NotNull(message = "Asset ID không được để trống")
    private UUID assetId;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private Integer sortOrder;
}
