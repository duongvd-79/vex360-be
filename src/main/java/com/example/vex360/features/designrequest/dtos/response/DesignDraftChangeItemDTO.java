package com.example.vex360.features.designrequest.dtos.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignDraftChangeItemDTO {
    public enum ChangeScope {
        PANORAMA,
        HOTSPOT,
        PRODUCT,
        MEDIA_ASSET,
        BOOTH_SETTINGS
    }

    public enum ChangeType {
        ADDED,
        MODIFIED,
        REMOVED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldChangeDTO {
        private String fieldName;
        private String oldValue;
        private String newValue;
    }

    private ChangeScope scope;
    private ChangeType changeType;
    private String targetName;
    private List<FieldChangeDTO> fieldChanges;
}
