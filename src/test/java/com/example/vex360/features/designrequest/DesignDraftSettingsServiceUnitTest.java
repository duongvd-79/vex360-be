package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignDraftSettingsService;

@ExtendWith(MockitoExtension.class)
class DesignDraftSettingsServiceUnitTest {
    @Mock
    private DesignDraftAssetService assetService;
    @Mock
    private DesignAssetReferenceService assetReferenceService;
    @Mock
    private DesignDraftAssetRepository assetRepository;

    private DesignDraftSettingsService service;

    @BeforeEach
    void setUp() {
        service = new DesignDraftSettingsService(assetService, assetReferenceService, assetRepository);
    }

    @Test
    void bulkKeepAttachesRequestBaselineAssets() {
        UUID requestId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .name("Booth")
                .displayTemplateKey("classic")
                .thumbnailPublicId("booth/thumbnail")
                .backgroundMusicPublicId("booth/music")
                .build();
        DesignRequest request = DesignRequest.builder().id(requestId).booth(booth).build();
        DesignDraftAsset thumbnailAsset = baseline("booth/thumbnail", DesignDraftAssetType.THUMBNAIL);
        DesignDraftAsset musicAsset = baseline("booth/music", DesignDraftAssetType.BACKGROUND_MUSIC);
        when(assetRepository.findByDesignRequestIdAndPublicId(requestId, "booth/thumbnail"))
                .thenReturn(Optional.of(thumbnailAsset));
        when(assetRepository.findByDesignRequestIdAndPublicId(requestId, "booth/music"))
                .thenReturn(Optional.of(musicAsset));
        DesignDraft draft = DesignDraft.builder().designRequest(request).versionNumber(0).build();

        service.applyToDraft(request, draft, null);

        assertSame(thumbnailAsset, draft.getThumbnailAsset());
        assertSame(musicAsset, draft.getBackgroundMusicAsset());
    }

    @Test
    void applyingReplacementSchedulesOldFilesForReferenceAwareCleanup() {
        Booth booth = Booth.builder()
                .thumbnailPublicId("booth/old-thumbnail")
                .backgroundMusicPublicId("booth/old-music")
                .build();
        DesignDraftAsset thumbnail = baseline("draft/new-thumbnail", DesignDraftAssetType.THUMBNAIL);
        DesignDraftAsset music = baseline("draft/new-music", DesignDraftAssetType.BACKGROUND_MUSIC);
        DesignDraft draft = DesignDraft.builder()
                .boothName("New booth")
                .displayTemplateKey("classic")
                .thumbnailAction(DesignDraftFileAction.REPLACE)
                .thumbnailAsset(thumbnail)
                .backgroundMusicAction(DesignDraftFileAction.REPLACE)
                .backgroundMusicAsset(music)
                .build();

        service.applyToBooth(booth, draft);

        verify(assetReferenceService).scheduleCleanup("booth/old-thumbnail", "image");
        verify(assetReferenceService).scheduleCleanup("booth/old-music", "video");
    }

    private DesignDraftAsset baseline(String publicId, DesignDraftAssetType type) {
        return DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .publicId(publicId)
                .url("https://cdn/" + publicId)
                .assetType(type)
                .build();
    }
}
