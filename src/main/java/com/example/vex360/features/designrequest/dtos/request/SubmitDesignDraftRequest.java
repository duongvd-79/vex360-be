package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
    @NotEmpty(message = "Draft phải có ít nhất một panorama")
    private List<SubmitDesignDraftPanoramaRequest> panoramas;

    public SubmitDesignDraftRequest(String note, List<SubmitDesignDraftPanoramaRequest> panoramas) {
        this(note, null, panoramas);
    }
}
