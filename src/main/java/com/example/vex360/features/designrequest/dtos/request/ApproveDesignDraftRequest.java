package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveDesignDraftRequest {

    @Deprecated(since = "referenced staging media promotion")
    @Schema(
            deprecated = true,
            description = "Ignored. Referenced staging media is promoted automatically.")
    private List<UUID> acceptedMediaAssetIds;
}
