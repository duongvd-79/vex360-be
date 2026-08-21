package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.services.DesignRequestBaselineService;
import com.example.vex360.features.user.entities.User;

@ExtendWith(MockitoExtension.class)
class DesignRequestBaselineServiceUnitTest {
    @Mock
    BoothDesignService boothDesignService;
    @Mock
    DesignDraftAssetRepository assetRepository;

    @Test
    void initialDesignCreatesSettingsOnlyWorkingDraft() {
        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Initial booth")
                .description("Description")
                .displayTemplateKey("classic")
                .build();
        DesignRequest request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .requestedBy(User.builder().id(UUID.randomUUID()).build())
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();

        new DesignRequestBaselineService(boothDesignService, assetRepository).createWorkingBaseline(request);

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        assertEquals("Initial booth", request.getDrafts().get(0).getBoothName());
        assertEquals("Description", request.getDrafts().get(0).getBoothDescription());
        assertEquals("classic", request.getDrafts().get(0).getDisplayTemplateKey());
        assertTrue(request.getDrafts().get(0).getPanoramas().isEmpty());
        verifyNoInteractions(boothDesignService, assetRepository);
    }

    @Test
    void initialDesignAttachesExistingBoothThumbnailSnapshot() {
        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Initial booth")
                .displayTemplateKey("classic")
                .thumbnailUrl("https://cdn/thumbnail.jpg")
                .thumbnailPublicId("thumbnail/original")
                .build();
        DesignRequest request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .requestedBy(User.builder().id(UUID.randomUUID()).build())
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
        when(assetRepository.findByDesignRequestIdAndPublicId(request.getId(), "thumbnail/original"))
                .thenReturn(Optional.empty());
        when(assetRepository.save(any(DesignDraftAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new DesignRequestBaselineService(boothDesignService, assetRepository).createWorkingBaseline(request);

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        assertNotNull(request.getDrafts().get(0).getThumbnailAsset());
        assertEquals("thumbnail/original",
                request.getDrafts().get(0).getThumbnailAsset().getPublicId());
        assertEquals(DesignDraftAssetSource.BOOTH_BASELINE,
                request.getDrafts().get(0).getThumbnailAsset().getAssetSource());
        verifyNoInteractions(boothDesignService);
    }

    @Test
    void redesignCloneReusesBaselineAssetWithoutUploadingOrChargingStorage() {
        UUID boothId = UUID.randomUUID();
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        Booth booth = Booth.builder().id(boothId).name("Booth").displayTemplateKey("classic").build();
        Panorama panorama = Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name("Entrance")
                .imageUrl("https://cdn/panorama.jpg")
                .imageKey("panorama/original")
                .orderIndex(0)
                .isDefault(true)
                .build();
        DesignRequest request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .requestedBy(exhibitor)
                .mode(DesignRequestMode.REDESIGN)
                .build();
        when(boothDesignService.findPanoramaDetailsByBoothId(boothId)).thenReturn(List.of(panorama));
        when(assetRepository.findByDesignRequestIdAndPublicId(request.getId(), "panorama/original"))
                .thenReturn(Optional.empty());

        new DesignRequestBaselineService(boothDesignService, assetRepository).createWorkingBaseline(request);

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        assertSame(panorama.getImageKey(), request.getDrafts().get(0).getPanoramas().get(0).getImageKey());
        ArgumentCaptor<DesignDraftAsset> asset = ArgumentCaptor.forClass(DesignDraftAsset.class);
        verify(assetRepository).save(asset.capture());
        assertEquals(DesignDraftAssetSource.BOOTH_BASELINE, asset.getValue().getAssetSource());
        assertEquals(DesignDraftAssetQuotaState.NONE, asset.getValue().getQuotaState());
    }
}
