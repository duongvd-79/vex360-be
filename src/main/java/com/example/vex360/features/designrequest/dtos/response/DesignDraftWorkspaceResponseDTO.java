package com.example.vex360.features.designrequest.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftWorkspaceResponseDTO {
    private UUID id;
    private Integer versionNumber;
    private Long revision;
    private Instant createdAt;
    private SubmitDesignDraftRequest content;
}
