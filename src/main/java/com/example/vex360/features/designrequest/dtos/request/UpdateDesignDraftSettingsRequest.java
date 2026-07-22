package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDesignDraftSettingsRequest {
    private String note;

    @NotBlank(message = "Ten booth khong duoc de trong")
    private String name;

    private String description;

    @NotBlank(message = "Display template key khong duoc de trong")
    private String displayTemplateKey;

    private DesignDraftFileAction thumbnailAction;
    private UUID thumbnailAssetId;
    private DesignDraftFileAction backgroundMusicAction;
    private UUID backgroundMusicAssetId;
}
