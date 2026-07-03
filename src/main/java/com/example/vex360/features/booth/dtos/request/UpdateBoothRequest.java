package com.example.vex360.features.booth.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothRequest {
    private String name;
    private String description;
    private String displayTemplateKey;
}
