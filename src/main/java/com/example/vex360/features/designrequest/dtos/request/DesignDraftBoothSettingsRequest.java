package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftBoothSettingsRequest {
    private String name;
    private String description;
    private String displayTemplateKey;
    private DesignDraftFileAction thumbnailAction;
    private UUID thumbnailAssetId;
    private DesignDraftFileAction backgroundMusicAction;
    private UUID backgroundMusicAssetId;
}
