package com.example.vex360.features.designrequest.services;

import static org.mockito.Mockito.verify;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class DesignAssetReferenceEventUnitTest {

    @Mock
    private BoothDesignService boothDesignService;
    @Mock
    private DesignDraftPanoramaRepository draftPanoramaRepository;
    @Mock
    private DesignDraftRepository draftRepository;
    @Mock
    private CloudService cloudService;
    @Mock
    private ExhibitorMediaAssetService exhibitorMediaAssetService;
    @Mock
    private DesignRequestMediaAssetRepository requestMediaAssetRepository;
    @InjectMocks
    private DesignAssetReferenceService service;

    @Test
    void handleCleanupRequestedDeletesUnreferencedAsset() {
        service.handleCleanupRequested(new PanoramaImageCleanupService.CleanupRequested(
                Set.of("draft/orphan"), "image"));

        verify(cloudService).delete("draft/orphan", "image");
    }
}
