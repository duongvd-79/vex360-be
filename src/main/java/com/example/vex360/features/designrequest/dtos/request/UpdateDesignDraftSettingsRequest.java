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

    @NotBlank(message = "Tên booth không được để trống")
    private String name;

    private String description;

    @NotBlank(message = "Mẫu hiển thị không được để trống")
    private String displayTemplateKey;

    private DesignDraftFileAction thumbnailAction;
    private UUID thumbnailAssetId;
    private DesignDraftFileAction backgroundMusicAction;
    private UUID backgroundMusicAssetId;
}
