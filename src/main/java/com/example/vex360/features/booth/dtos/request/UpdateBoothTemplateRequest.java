package com.example.vex360.features.booth.dtos.request;

import com.example.vex360.features.booth.enums.BoothStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothTemplateRequest {
    private String name;
    private String description;
    private BoothStatus status;
}
