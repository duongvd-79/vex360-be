package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignDraftPreviewBoothResponseDTO {
    private String name;
    private String description;
    private String thumbnailUrl;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
    private String displayTemplateKey;
    private List<DesignDraftPanoramaResponseDTO> panoramas;
}
