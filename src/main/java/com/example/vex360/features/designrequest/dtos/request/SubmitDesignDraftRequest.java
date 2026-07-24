package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftRequest {
    private String note;

    @Valid
    private DesignDraftBoothSettingsRequest boothSettings;

    @Valid
    @NotNull(message = "Danh sách panorama không được để trống")
    private List<SubmitDesignDraftPanoramaRequest> panoramas;

    @Valid
    private List<SubmitDesignDraftMediaAssetRequest> mediaAssets;

    public SubmitDesignDraftRequest(String note, List<SubmitDesignDraftPanoramaRequest> panoramas) {
        this(note, null, panoramas, null);
    }
}


