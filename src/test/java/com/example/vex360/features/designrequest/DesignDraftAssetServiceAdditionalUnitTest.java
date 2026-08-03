package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftMediaAssetRepository;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class DesignDraftAssetServiceAdditionalUnitTest {

    @Mock
    private DesignDraftAssetRepository assetRepository;
    @Mock
    private DesignDraftMediaAssetRepository draftMediaAssetRepository;
    @Mock
    private DesignerWorkspaceService workspaceService;
    @Mock
    private CompanyStorageService storageService;
    @Mock
    private CloudService cloudService;
    @Mock
    private BoothDesignService boothDesignService;
    @Mock
    private DesignAssetReferenceService assetReferenceService;

    private DesignDraftAssetService service;
    private DesignRequest request;

    @BeforeEach
    void setUp() {
        service = new DesignDraftAssetService(
                assetRepository,
                draftMediaAssetRepository,
                workspaceService,
                storageService,
                cloudService,
                boothDesignService,
                assetReferenceService);
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .build();
    }

    @Test
    void requireDraftAssetByPublicIdReturnsMatchingPanorama() {
        DesignDraftAsset asset = asset("pano/a", "https://cdn/a.jpg", DesignDraftAssetType.PANORAMA);
        when(assetRepository.findByDesignRequestIdAndPublicId(request.getId(), asset.getPublicId()))
                .thenReturn(Optional.of(asset));

        DesignDraftAsset result = service.requireDraftAsset(request, asset.getPublicId(), asset.getUrl());

        assertSame(asset, result);
    }

    @Test
    void requireDraftAssetByPublicIdRejectsMismatchedUrl() {
        DesignDraftAsset asset = asset("pano/a", "https://cdn/a.jpg", DesignDraftAssetType.PANORAMA);
        when(assetRepository.findByDesignRequestIdAndPublicId(request.getId(), asset.getPublicId()))
                .thenReturn(Optional.of(asset));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.requireDraftAsset(request, asset.getPublicId(), "https://cdn/other.jpg"));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    @Test
    void requireDraftAssetByIdReturnsMatchingType() {
        DesignDraftAsset asset = asset("media/a", "https://cdn/a.mp4", DesignDraftAssetType.MEDIA_ATTACHMENT);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));

        DesignDraftAsset result = service.requireDraftAsset(
                request, asset.getId(), DesignDraftAssetType.MEDIA_ATTACHMENT);

        assertSame(asset, result);
    }

    @Test
    void requireDraftAssetByIdRejectsWrongType() {
        DesignDraftAsset asset = asset("media/a", "https://cdn/a.mp4", DesignDraftAssetType.MEDIA_ATTACHMENT);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.requireDraftAsset(request, asset.getId(), DesignDraftAssetType.THUMBNAIL));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    @Test
    void cleanupUnreferencedAssetsDeletesOnlyUnusedAsset() {
        DesignDraftAsset used = asset("pano/used", "https://cdn/used.jpg", DesignDraftAssetType.PANORAMA);
        DesignDraftAsset unused = asset("pano/unused", "https://cdn/unused.jpg", DesignDraftAssetType.PANORAMA);
        DesignDraftPanorama panorama = DesignDraftPanorama.builder().imageKey(used.getPublicId()).build();
        request.getDrafts().add(DesignDraft.builder().versionNumber(0).panoramas(List.of(panorama)).build());
        when(boothDesignService.getPanoramaImageKeys(request.getBooth().getId())).thenReturn(Set.of());
        when(assetRepository.findByDesignRequestId(request.getId())).thenReturn(List.of(used, unused));

        int deleted = service.cleanupUnreferencedAssets(request);

        assertEquals(1, deleted);
        verify(assetRepository).delete(unused);
        verify(assetRepository, never()).delete(used);
        verify(assetReferenceService).scheduleCleanup(unused.getPublicId(), "image");
    }

    private DesignDraftAsset asset(String publicId, String url, DesignDraftAssetType type) {
        return DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .publicId(publicId)
                .url(url)
                .mimeType(type == DesignDraftAssetType.MEDIA_ATTACHMENT ? "video/mp4" : "image/jpeg")
                .fileSize(10L)
                .assetType(type)
                .quotaState(DesignDraftAssetQuotaState.STAGED)
                .build();
    }
}
