package com.example.vex360.features.designrequest.dtos.response;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftSettingsResponseDTO {
    private String note;
    private String name;
    private String description;
    private String displayTemplateKey;
    private DesignDraftFileAction thumbnailAction;
    private UUID thumbnailAssetId;
    private String thumbnailUrl;
    private DesignDraftFileAction backgroundMusicAction;
    private UUID backgroundMusicAssetId;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
}
