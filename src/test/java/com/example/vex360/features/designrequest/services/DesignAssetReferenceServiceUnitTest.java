package com.example.vex360.features.designrequest.services;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class DesignAssetReferenceServiceUnitTest {
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private DesignDraftPanoramaRepository draftPanoramaRepository;
    @Mock
    private DesignDraftRepository draftRepository;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private CloudService cloudService;

    private DesignAssetReferenceService service;

    @BeforeEach
    void setUp() {
        service = new DesignAssetReferenceService(
                panoramaRepository,
                boothRepository,
                draftPanoramaRepository,
                draftRepository,
                mediaAssetRepository,
                cloudService);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cleanupKeepsImageReferencedByRetainedDraft() {
        when(draftPanoramaRepository.existsByImageKey("draft/image-a")).thenReturn(true);

        service.scheduleCleanup("draft/image-a", "image");

        verify(cloudService, never()).delete("draft/image-a", "image");
    }

    @Test
    void cleanupKeepsThumbnailReferencedByRetainedDraftSettings() {
        when(draftRepository.existsByThumbnailOrBackgroundMusicPublicId("draft/thumbnail-a"))
                .thenReturn(true);

        service.scheduleCleanup("draft/thumbnail-a", "image");

        verify(cloudService, never()).delete("draft/thumbnail-a", "image");
    }

    @Test
    void cleanupKeepsPromotedCompanyMedia() {
        when(mediaAssetRepository.existsByPublicId("design-media/approved")).thenReturn(true);

        service.scheduleCleanup("design-media/approved", "video");

        verify(cloudService, never()).delete("design-media/approved", "video");
    }

    @Test
    void cleanupDeletesOnlyGloballyUnreferencedAsset() {
        service.scheduleCleanup("draft/image-unused", "image");

        verify(cloudService).delete("draft/image-unused", "image");
    }

    @Test
    void rollbackDoesNotDeleteCloudinaryAsset() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.scheduleCleanup("draft/image-a", "image");

        verify(cloudService, never()).delete("draft/image-a", "image");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(cloudService, never()).delete("draft/image-a", "image");
    }

    @Test
    void cleanupRunsOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.scheduleCleanup("draft/image-a", "image");

        verify(cloudService, never()).delete("draft/image-a", "image");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(cloudService).delete("draft/image-a", "image");
    }
}
