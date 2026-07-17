package com.example.vex360.features.designrequest.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectDesignDraftRequest {
    private String reviewNote;
}
