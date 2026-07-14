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
    @NotEmpty(message = "Draft phai co it nhat mot panorama")
    private List<SubmitDesignDraftPanoramaRequest> panoramas;
}
