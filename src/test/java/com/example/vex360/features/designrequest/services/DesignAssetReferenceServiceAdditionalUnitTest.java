package com.example.vex360.features.designrequest.services;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class DesignAssetReferenceServiceAdditionalUnitTest {

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

    private DesignAssetReferenceService service;

    @BeforeEach
    void setUp() {
        service = new DesignAssetReferenceService(
                boothDesignService,
                draftPanoramaRepository,
                draftRepository,
                cloudService,
                exhibitorMediaAssetService,
                requestMediaAssetRepository);
    }

    @Test
    void deleteMediaAssetRejectsAssetUsedByActiveRequest() {
        UUID assetId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).build();
        when(requestMediaAssetRepository.existsByMediaAssetIdAndRequestStatusIn(
                assetId, com.example.vex360.features.designrequest.repositories.DesignRequestRepository.NON_TERMINAL_STATUSES))
                .thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteMediaAsset(user, assetId));

        assertSame(ErrorCode.DESIGN_MEDIA_ASSET_LOCKED, exception.getErrorCode());
        verify(exhibitorMediaAssetService, never()).deleteMediaAsset(user, assetId);
    }

    @Test
    void deleteMediaAssetDeletesRecordAndCleansVideoResource() {
        UUID assetId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).build();
        MediaAssetResponseDTO deleted = new MediaAssetResponseDTO();
        deleted.setId(assetId);
        deleted.setPublicId("company/video-a");
        deleted.setType(MediaAssetType.VIDEO);
        when(exhibitorMediaAssetService.deleteMediaAsset(user, assetId)).thenReturn(deleted);

        MediaAssetResponseDTO response = service.deleteMediaAsset(user, assetId);

        assertSame(deleted, response);
        verify(cloudService).delete("company/video-a", "video");
    }
}
