package com.example.vex360.features.company.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoragePackageResponseDTO {
    Integer id;
    String name;
    String description;
    Long quotaBytes;
    Long priceVnd;
}
