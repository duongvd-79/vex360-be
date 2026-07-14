package com.example.vex360.features.booth.dtos.response;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothReviewChangeScope;
import com.example.vex360.features.booth.enums.BoothReviewChangeType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewChangeItemDTO {
    private BoothReviewChangeType type;
    private BoothReviewChangeScope scope;
    private UUID itemId;
    private String itemName;
    private UUID parentId;
    private String parentName;
    private UUID panoramaId;
    private String panoramaName;
    private List<String> fields;
    private List<BoothReviewFieldChangeDTO> fieldChanges;
}
