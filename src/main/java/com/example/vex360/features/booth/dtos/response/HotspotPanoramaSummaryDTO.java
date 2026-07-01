package com.example.vex360.features.booth.dtos.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotPanoramaSummaryDTO {
    private UUID id;
    private String name;
}
