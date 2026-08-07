package com.example.vex360.features.designrequest.dtos.request;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderDesignDraftMediaAssetsRequest {

    @NotEmpty(message = "Danh sách media không được để trống.")
    private List<@NotNull(message = "ID media không được để trống.") UUID> mediaAssetIds;

    @JsonIgnore
    @AssertTrue(message = "Danh sách media không được chứa ID trùng lặp.")
    public boolean isMediaAssetIdsUnique() {
        return mediaAssetIds == null || new HashSet<>(mediaAssetIds).size() == mediaAssetIds.size();
    }
}
