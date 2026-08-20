package com.example.vex360.features.booth.repositories;

import java.util.UUID;

import com.example.vex360.shared.enums.BoothListingPriority;

public interface ProductPlacementProjection {
    UUID getProductId();
    UUID getBoothId();
    String getBoothName();
    String getBoothThumbnailUrl();
    BoothListingPriority getListingPrioritySnapshot();
    BoothListingPriority getPackageListingPriority();
    UUID getPanoramaId();
    String getPanoramaName();
    UUID getHotspotId();
}
