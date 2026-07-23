package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;

import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.services.DesignDraftCloneService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

class DesignDraftCloneServiceUnitTest {

    private final DesignDraftCloneService service = new DesignDraftCloneService();

    @Test
    void cloneLatestSubmittedCreatesIndependentVersionZeroGraph() {
        DesignRequest request = request();
        DesignDraftAsset thumbnail = DesignDraftAsset.builder().id(UUID.randomUUID()).build();
        MediaAsset mediaAsset = MediaAsset.builder().id(UUID.randomUUID()).build();
        DesignDraft submitted = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(2)
                .note("Submitted note")
                .boothName("Approved name")
                .boothDescription("Description")
                .displayTemplateKey("classic")
                .thumbnailAction(DesignDraftFileAction.REPLACE)
                .thumbnailAsset(thumbnail)
                .backgroundMusicAction(DesignDraftFileAction.CLEAR)
                .build();
        DesignDraftPanorama sourcePanorama = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(submitted)
                .clientKey("entrance")
                .name("Entrance")
                .imageUrl("https://cdn/entrance.jpg")
                .imageKey("panorama/entrance")
                .orderIndex(0)
                .isDefault(true)
                .build();
        DesignDraftHotspot sourceHotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(sourcePanorama)
                .type(HotspotType.MEDIA)
                .name("Video")
                .mediaAsset(mediaAsset)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .mediaClickAction(HotspotMediaClickAction.DEFAULT)
                .cornerTlX(1.0)
                .cornerTlY(2.0)
                .cornerTlZ(3.0)
                .build();
        sourcePanorama.getHotspots().add(sourceHotspot);
        submitted.getPanoramas().add(sourcePanorama);
        request.getDrafts().add(submitted);

        DesignDraftMediaAsset sourceMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(submitted)
                .asset(thumbnail)
                .title("Sample Attachment")
                .sortOrder(1)
                .build();
        submitted.getMediaAssets().add(sourceMedia);

        DesignDraft working = service.cloneLatestSubmittedToWorking(request);

        assertEquals(0, working.getVersionNumber());
        assertEquals(2, submitted.getVersionNumber());
        assertEquals(2, request.getDrafts().size());
        assertEquals("Approved name", working.getBoothName());
        assertSame(thumbnail, working.getThumbnailAsset());
        assertNotSame(submitted, working);
        assertNotSame(sourcePanorama, working.getPanoramas().get(0));
        assertNotSame(sourceHotspot, working.getPanoramas().get(0).getHotspots().get(0));
        assertSame(mediaAsset, working.getPanoramas().get(0).getHotspots().get(0).getMediaAsset());
        assertSame(working, working.getPanoramas().get(0).getDraft());
        assertSame(working.getPanoramas().get(0),
                working.getPanoramas().get(0).getHotspots().get(0).getSourcePanorama());
        assertEquals(1, working.getMediaAssets().size());
        assertNotSame(sourceMedia, working.getMediaAssets().get(0));
        assertEquals("Sample Attachment", working.getMediaAssets().get(0).getTitle());
        assertSame(thumbnail, working.getMediaAssets().get(0).getAsset());

    }

    @Test
    void cloneRejectsRequestThatAlreadyHasWorkingDraft() {
        DesignRequest request = request();
        request.getDrafts().add(DesignDraft.builder()
                .designRequest(request)
                .versionNumber(1)
                .build());
        request.getDrafts().add(DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .build());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.cloneLatestSubmittedToWorking(request));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    private DesignRequest request() {
        return DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
