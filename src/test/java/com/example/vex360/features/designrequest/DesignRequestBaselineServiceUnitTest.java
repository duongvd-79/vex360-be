package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
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
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignRequestScope;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.services.DesignRequestBaselineService;
import com.example.vex360.features.user.entities.User;

@ExtendWith(MockitoExtension.class)
class DesignRequestBaselineServiceUnitTest {
    @Mock
    PanoramaRepository panoramaRepository;
    @Mock
    DesignDraftAssetRepository assetRepository;

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
                .scope(DesignRequestScope.FULL)
                .build();
        when(panoramaRepository.findDetailsByBoothId(boothId)).thenReturn(List.of(panorama));
        when(assetRepository.findByDesignRequestIdAndPublicId(request.getId(), "panorama/original"))
                .thenReturn(Optional.empty());

        new DesignRequestBaselineService(panoramaRepository, assetRepository).createWorkingBaseline(request);

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        assertSame(panorama.getImageKey(), request.getDrafts().get(0).getPanoramas().get(0).getImageKey());
        ArgumentCaptor<DesignDraftAsset> asset = ArgumentCaptor.forClass(DesignDraftAsset.class);
        verify(assetRepository).save(asset.capture());
        assertEquals(DesignDraftAssetSource.BOOTH_BASELINE, asset.getValue().getAssetSource());
    }
}
