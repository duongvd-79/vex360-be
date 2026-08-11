package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeScope;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeType;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeSummaryDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.services.DesignDraftCloneService;
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

    @Test
    void compareUnchangedRedesignBaselineWithBooth_ReturnsNoChanges() {
        UUID panoramaId = UUID.randomUUID();
        Panorama boothPanorama = Panorama.builder()
                .id(panoramaId)
                .imageKey("panorama/showroom")
                .name("Showroom")
                .orderIndex(0)
                .isDefault(true)
                .build();
        Hotspot boothHotspot = Hotspot.builder()
                .name("Entrance")
                .sourcePanorama(boothPanorama)
                .targetPanorama(boothPanorama)
                .type(HotspotType.NAV)
                .xPosition(0.0)
                .yPosition(0.0)
                .zPosition(0.0)
                .build();
        boothPanorama.setHotspots(List.of(boothHotspot));
        Booth booth = Booth.builder()
                .name("Booth")
                .description("Description")
                .panoramas(List.of(boothPanorama))
                .build();

        DesignDraftPanorama draftPanorama = DesignDraftPanorama.builder()
                .clientKey(panoramaId.toString())
                .imageKey("panorama/showroom")
                .name("Showroom")
                .orderIndex(0)
                .isDefault(true)
                .build();
        draftPanorama.setHotspots(List.of(DesignDraftHotspot.builder()
                .name("Entrance")
                .sourcePanorama(draftPanorama)
                .targetDraftPanoramaKey(panoramaId.toString())
                .type(HotspotType.NAV)
                .xPosition(0.0)
                .yPosition(0.0)
                .zPosition(0.0)
                .build()));
        DesignDraft draft = DesignDraft.builder()
                .boothName("Booth")
                .boothDescription("Description")
                .panoramas(List.of(draftPanorama))
                .build();

        DesignDraftChangeSummaryDTO summary = diffService.compareDraftWithBooth(draft, booth);

        assertEquals(0, summary.getTotalCount());
    }

    @Test
    void sameDesignContentIgnoresIdentityMetadataNoteAndCollectionOrder() {
        DesignDraft previous = fullDraft();
        DesignDraft working = cloneOf(previous);
        working.setId(UUID.randomUUID());
        working.setNote("A different note");
        working.setVersionNumber(9);
        working.setRejectionReason("Ignored");
        working.getPanoramas().forEach(panorama -> {
            panorama.setId(UUID.randomUUID());
            panorama.getHotspots().forEach(hotspot -> hotspot.setId(UUID.randomUUID()));
            Collections.reverse(panorama.getHotspots());
        });
        working.getMediaAssets().forEach(media -> media.setId(UUID.randomUUID()));
        Collections.reverse(working.getPanoramas());
        Collections.reverse(working.getMediaAssets());

        assertTrue(diffService.hasSameDesignContent(working, previous));
    }

    @Test
    void sameDesignContentDetectsSettingsAndPanoramaChanges() {
        assertDetected(draft -> draft.setBoothDescription("Changed"));
        assertDetected(draft -> draft.setThumbnailAction(DesignDraftFileAction.CLEAR));
        assertDetected(draft -> draft.setThumbnailAsset(DesignDraftAsset.builder().id(UUID.randomUUID()).build()));
        assertDetected(draft -> panorama(draft, "p1").setImageKey("changed/key"));
        assertDetected(draft -> panorama(draft, "p1").setOrderIndex(2));
    }

    @Test
    void sameDesignContentDetectsExactHotspotAndLinkedEntityChanges() {
        assertDetected(draft -> hotspot(draft, "Nav").setXPosition(1.001));
        assertDetected(draft -> hotspot(draft, "Nav").setZIndex(99));
        assertDetected(draft -> hotspot(draft, "Nav").setCornerTlX(9.0));
        assertDetected(draft -> hotspot(draft, "Product")
                .setProduct(Product.builder().id(UUID.randomUUID()).build()));
        assertDetected(draft -> hotspot(draft, "Live media")
                .setMediaAsset(MediaAsset.builder().id(UUID.randomUUID()).build()));
        assertDetected(draft -> hotspot(draft, "Draft media").getDesignDraftMediaAsset()
                .setAsset(DesignDraftAsset.builder().id(UUID.randomUUID()).build()));
    }

    @Test
    void sameDesignContentDetectsDraftMediaMetadataChanges() {
        assertDetected(draft -> draft.getMediaAssets().get(0).setTitle("Changed"));
        assertDetected(draft -> draft.getMediaAssets().get(0).setSortOrder(7));
        assertDetected(draft -> draft.getMediaAssets().get(0)
                .setAsset(DesignDraftAsset.builder().id(UUID.randomUUID()).build()));
    }

    private void assertDetected(Consumer<DesignDraft> change) {
        DesignDraft previous = fullDraft();
        DesignDraft working = cloneOf(previous);
        change.accept(working);
        assertFalse(diffService.hasSameDesignContent(working, previous));
    }

    private DesignDraft cloneOf(DesignDraft previous) {
        DesignRequest request = DesignRequest.builder().build();
        previous.setDesignRequest(request);
        request.getDrafts().add(previous);
        return new DesignDraftCloneService().cloneLatestSubmittedToWorking(request);
    }

    private DesignDraft fullDraft() {
        DesignDraftAsset thumbnail = DesignDraftAsset.builder().id(UUID.randomUUID()).build();
        DesignDraftAsset music = DesignDraftAsset.builder().id(UUID.randomUUID()).build();
        DesignDraftAsset firstMediaAsset = DesignDraftAsset.builder().id(UUID.randomUUID()).build();
        DesignDraftAsset secondMediaAsset = DesignDraftAsset.builder().id(UUID.randomUUID()).build();
        Product product = Product.builder().id(UUID.randomUUID()).name("Product").build();
        MediaAsset liveMedia = MediaAsset.builder().id(UUID.randomUUID()).name("Live media").build();
        DesignDraft draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .note("Original note")
                .boothName("Booth")
                .boothDescription("Description")
                .displayTemplateKey("template")
                .thumbnailAction(DesignDraftFileAction.REPLACE)
                .thumbnailAsset(thumbnail)
                .backgroundMusicAction(DesignDraftFileAction.REPLACE)
                .backgroundMusicAsset(music)
                .build();
        DesignDraftMediaAsset firstMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(firstMediaAsset)
                .title("First")
                .sortOrder(0)
                .build();
        DesignDraftMediaAsset secondMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(secondMediaAsset)
                .title("Second")
                .sortOrder(1)
                .build();
        draft.setMediaAssets(new ArrayList<>(List.of(firstMedia, secondMedia)));

        DesignDraftPanorama source = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .clientKey("p1")
                .name("Source")
                .imageUrl("https://cdn.example.com/source.jpg")
                .imageKey("source/key")
                .orderIndex(0)
                .isDefault(true)
                .build();
        DesignDraftPanorama target = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .clientKey("p2")
                .name("Target")
                .imageUrl("https://cdn.example.com/target.jpg")
                .imageKey("target/key")
                .orderIndex(1)
                .isDefault(false)
                .build();
        source.setHotspots(new ArrayList<>(List.of(
                DesignDraftHotspot.builder()
                        .id(UUID.randomUUID())
                        .sourcePanorama(source)
                        .type(HotspotType.NAV)
                        .name("Nav")
                        .targetDraftPanoramaKey("p2")
                        .xPosition(1.0).yPosition(2.0).zPosition(3.0)
                        .iconStyle("arrow").scale(1.5).zIndex(2)
                        .cornerTlX(0.0).cornerTlY(0.1).cornerTlZ(0.2)
                        .cornerTrX(1.0).cornerTrY(1.1).cornerTrZ(1.2)
                        .cornerBlX(2.0).cornerBlY(2.1).cornerBlZ(2.2)
                        .cornerBrX(3.0).cornerBrY(3.1).cornerBrZ(3.2)
                        .build(),
                DesignDraftHotspot.builder()
                        .id(UUID.randomUUID())
                        .sourcePanorama(source)
                        .type(HotspotType.PRODUCT)
                        .name("Product")
                        .product(product)
                        .xPosition(4.0).yPosition(5.0).zPosition(6.0)
                        .build(),
                DesignDraftHotspot.builder()
                        .id(UUID.randomUUID())
                        .sourcePanorama(source)
                        .type(HotspotType.MEDIA)
                        .name("Live media")
                        .mediaAsset(liveMedia)
                        .mediaClickAction(HotspotMediaClickAction.DEFAULT)
                        .xPosition(7.0).yPosition(8.0).zPosition(9.0)
                        .build(),
                DesignDraftHotspot.builder()
                        .id(UUID.randomUUID())
                        .sourcePanorama(source)
                        .type(HotspotType.MEDIA)
                        .name("Draft media")
                        .designDraftMediaAsset(firstMedia)
                        .mediaClickAction(HotspotMediaClickAction.NONE)
                        .xPosition(10.0).yPosition(11.0).zPosition(12.0)
                        .build(),
                DesignDraftHotspot.builder()
                        .id(UUID.randomUUID())
                        .sourcePanorama(source)
                        .type(HotspotType.INFO)
                        .name("Info")
                        .infoText("Details")
                        .infoContentType(HotspotInfoContentType.TEXT)
                        .xPosition(13.0).yPosition(14.0).zPosition(15.0)
                        .build())));
        draft.setPanoramas(new ArrayList<>(List.of(source, target)));
        return draft;
    }

    private DesignDraftPanorama panorama(DesignDraft draft, String clientKey) {
        return draft.getPanoramas().stream()
                .filter(panorama -> clientKey.equals(panorama.getClientKey()))
                .findFirst()
                .orElseThrow();
    }

    private DesignDraftHotspot hotspot(DesignDraft draft, String name) {
        return draft.getPanoramas().stream()
                .flatMap(panorama -> panorama.getHotspots().stream())
                .filter(hotspot -> name.equals(hotspot.getName()))
                .findFirst()
                .orElseThrow();
    }
}
