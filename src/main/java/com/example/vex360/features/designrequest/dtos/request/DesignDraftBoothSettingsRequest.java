package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftBoothSettingsRequest {
    @Size(max = 255, message = "Tên booth không được vượt quá 255 ký tự.")
    private String name;
    @Size(max = 5000, message = "Mô tả booth không được vượt quá 5000 ký tự.")
    private String description;
    @Size(max = 100, message = "Display template key không được vượt quá 100 ký tự.")
    private String displayTemplateKey;
    private DesignDraftFileAction thumbnailAction;
    private UUID thumbnailAssetId;
    private DesignDraftFileAction backgroundMusicAction;
    private UUID backgroundMusicAssetId;
}
