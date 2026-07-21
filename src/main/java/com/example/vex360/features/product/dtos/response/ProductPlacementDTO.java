package com.example.vex360.features.product.dtos.response;

import java.util.UUID;

import com.example.vex360.shared.enums.BoothListingPriority;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPlacementDTO {
    private UUID boothId;
    private String boothName;
    private String boothThumbnailUrl;
    private BoothListingPriority listingPriority;
    private UUID panoramaId;
    private String panoramaName;
    private UUID hotspotId;
}
