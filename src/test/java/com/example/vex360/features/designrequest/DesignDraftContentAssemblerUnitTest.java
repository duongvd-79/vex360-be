package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.services.DesignDraftContentAssembler;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;

class DesignDraftContentAssemblerUnitTest {

    private DesignDraftContentAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new DesignDraftContentAssembler();
    }

    @Test
    void toDraftContentOverview_NullDraft_ReturnsZeroOverview() {
        BoothReviewContentOverviewDTO overview = assembler.toDraftContentOverview(null);

        assertNotNull(overview);
        assertEquals(0, overview.getPanoramaCount());
        assertEquals(0, overview.getHotspotCount());
    }

    @Test
    void toDraftContentOverview_WithPanoramasAndHotspots_AssemblesOverview() {
        ProductContent content = ProductContent.builder()
                .id(UUID.randomUUID())
                .orderIndex(1)
                .contentUrl("http://content.url")
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Sample Product")
                .contents(List.of(content))
                .build();

        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .name("Sample Media")
                .url("http://media.url")
                .build();

        DesignDraftAsset draftAsset = DesignDraftAsset.builder()
                .fileName("staging.jpg")
                .url("http://staging.url")
                .fileSize(1024L)
                .build();

        DesignDraftMediaAsset draftMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .asset(draftAsset)
                .build();

        DesignDraftHotspot productHotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .type(HotspotType.PRODUCT)
                .name("Product Hotspot")
                .product(product)
                .build();

        DesignDraftHotspot mediaHotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .type(HotspotType.MEDIA)
                .name("Media Hotspot")
                .mediaAsset(mediaAsset)
                .build();

        DesignDraftHotspot draftMediaHotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .type(HotspotType.MEDIA)
                .name("Draft Media Hotspot")
                .designDraftMediaAsset(draftMedia)
                .build();

        DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .name("Main Pano")
                .orderIndex(1)
                .hotspots(List.of(productHotspot, mediaHotspot, draftMediaHotspot))
                .build();

        DesignDraft draft = DesignDraft.builder()
                .panoramas(List.of(panorama))
                .build();

        BoothReviewContentOverviewDTO overview = assembler.toDraftContentOverview(draft);

        assertNotNull(overview);
        assertEquals(1, overview.getPanoramaCount());
        assertEquals(3, overview.getHotspotCount());
        assertEquals(1, overview.getProductCount());
        assertEquals(2, overview.getMediaAssetCount());
    }
}
