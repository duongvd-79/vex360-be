package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDesignDraftRequest {
    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự.")
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


