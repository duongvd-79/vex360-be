package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveDesignDraftRequest {

    private List<UUID> acceptedMediaAssetIds;
}
