package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeScope;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeType;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeSummaryDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.services.DesignDraftDiffService;
import com.example.vex360.features.product.entities.Product;

class DesignDraftDiffServiceTest {

    private DesignDraftDiffService diffService;

    @BeforeEach
    void setup() {
        diffService = new DesignDraftDiffService();
    }

    @Test
    void compareDraftWithPrevious_DetectsAddedAndRemovedPanoramas() {
        DesignDraft previous = DesignDraft.builder()
                .versionNumber(1)
                .boothName("Booth V1")
                .panoramas(List.of(
                        DesignDraftPanorama.builder().clientKey("pano-1").name("Panorama 1").orderIndex(0).isDefault(true).build()
                ))
                .build();

        DesignDraft current = DesignDraft.builder()
                .versionNumber(2)
                .boothName("Booth V2")
                .panoramas(List.of(
                        DesignDraftPanorama.builder().clientKey("pano-1").name("Panorama 1 Renamed").orderIndex(0).isDefault(true).build(),
                        DesignDraftPanorama.builder().clientKey("pano-2").name("Panorama 2").orderIndex(1).isDefault(false).build()
                ))
                .build();

        DesignDraftChangeSummaryDTO summary = diffService.compareDraftWithPrevious(current, previous);

        assertNotNull(summary);
        assertEquals(1, summary.getAddedCount()); // Panorama 2 added
        assertEquals(2, summary.getModifiedCount()); // Booth name changed & Panorama 1 renamed
    }

    @Test
    void compareDraftWithBooth_DetectsDifferencesIncludingHotspots() {
        Panorama boothPano = Panorama.builder()
                .id(UUID.randomUUID())
                .imageKey("pano-key-1")
                .name("Booth Pano")
                .build();

        Hotspot boothHotspot = Hotspot.builder()
                .id(UUID.randomUUID())
                .name("Hotspot 1")
                .sourcePanorama(boothPano)
                .type(HotspotType.NAV)
                .xPosition(0.0)
                .yPosition(0.0)
                .zPosition(0.0)
                .build();
        boothPano.setHotspots(List.of(boothHotspot));

        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Old Booth")
                .panoramas(List.of(boothPano))
                .build();

        Product product = Product.builder().id(UUID.randomUUID()).name("Smart TV").build();
        DesignDraftPanorama draftPano = DesignDraftPanorama.builder()
                .imageKey("pano-key-1")
                .name("Booth Pano")
                .orderIndex(0)
                .isDefault(true)
                .build();
        DesignDraftHotspot draftHotspot = DesignDraftHotspot.builder()
                .name("Hotspot 1")
                .sourcePanorama(draftPano)
                .type(HotspotType.PRODUCT)
                .product(product)
                .xPosition(1.5)
                .yPosition(2.0)
                .zPosition(0.0)
                .build();
        draftPano.setHotspots(List.of(draftHotspot));

        DesignDraft current = DesignDraft.builder()
                .versionNumber(1)
                .boothName("New Booth")
                .panoramas(List.of(draftPano))
                .build();

        DesignDraftChangeSummaryDTO summary = diffService.compareDraftWithBooth(current, booth);

        assertNotNull(summary);
        // Booth name modified, Hotspot 1 modified (type NAV -> PRODUCT, position (0,0,0) -> (1.5,2.0,0)), Product Smart TV added
        assertTrue(summary.getTotalCount() > 0);
        assertTrue(summary.getItems().stream().anyMatch(i -> i.getScope() == ChangeScope.HOTSPOT && i.getChangeType() == ChangeType.MODIFIED));
        assertTrue(summary.getItems().stream().anyMatch(i -> i.getScope() == ChangeScope.PRODUCT && i.getChangeType() == ChangeType.ADDED));
    }
}
