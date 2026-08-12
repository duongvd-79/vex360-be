package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftMediaAssetRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class DesignDraftAssetServiceUnitTest {
    @Mock
    DesignDraftAssetRepository assetRepository;
    @Mock
    DesignDraftMediaAssetRepository draftMediaAssetRepository;
    @Mock
    DesignerWorkspaceService workspaceService;
    @Mock
    CompanyStorageService storageService;
    @Mock
    CloudService cloudService;
    @Mock
    BoothDesignService boothDesignService;
    @Mock
    DesignAssetReferenceService assetReferenceService;

    private DesignDraftAssetService service;
    private User designer;
    private Company company;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignDraftAssetService(
                assetRepository,
                draftMediaAssetRepository,
                workspaceService,
                storageService,
                cloudService,
                boothDesignService,
                assetReferenceService);
        designer = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).storageUsedBytes(0L).storageQuotaBytes(1_000_000L).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .build();
    }

    @Test
    void uploadPanoramaStagesWithoutChangingCompanyQuota() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "pano.jpg", "image/jpeg", "image".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://cdn.example/pano.jpg")
                .publicId("panorama/pano")
                .fileName("pano.jpg")
                .fileSize(5L)
                .fileType("image/jpeg")
                .build();
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(cloudService.uploadToFolder(file, "panorama")).thenReturn(upload);
        when(assetRepository.save(any(DesignDraftAsset.class))).thenAnswer(invocation -> {
            DesignDraftAsset asset = invocation.getArgument(0);
            asset.setId(UUID.randomUUID());
            return asset;
        });

        DesignDraftAssetResponseDTO response = service.uploadPanorama(designer, request.getId(), file);

        assertEquals("panorama/pano", response.getImageKey());
        assertEquals(DesignDraftAssetQuotaState.NONE, response.getQuotaState());
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadMediaStagesWithoutChangingCompanyQuota() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "intro.mp4", "video/mp4", "video".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://cdn.example/intro.mp4")
                .publicId("design-media/intro")
                .fileName("intro.mp4")
                .fileSize(5L)
                .fileType("video/mp4")
                .build();
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(cloudService.uploadToFolder(file, "design-draft-media-attachment")).thenReturn(upload);
        when(assetRepository.save(any(DesignDraftAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DesignDraftAssetResponseDTO response = service.uploadAsset(
                designer,
                request.getId(),
                file,
                DesignDraftAssetType.MEDIA_ATTACHMENT);

        assertEquals(DesignDraftAssetQuotaState.STAGED, response.getQuotaState());
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadMediaRejectsUnsupportedMimeTypeBeforeCloudUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "pdf".getBytes());
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT));

        assertSame(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
        verify(storageService, never()).reserveUsage(any(), any(Long.class));
    }

    @Test
    void uploadMediaRejectsEmptyFileBeforeQuotaReservation() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT));

        assertSame(ErrorCode.PANORAMA_FILE_INVALID, exception.getErrorCode());
        verify(storageService, never()).reserveUsage(any(), any(Long.class));
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void uploadPanoramaRejectsMissingFileWithPanoramaError() {
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.uploadAsset(designer, request.getId(), null, DesignDraftAssetType.PANORAMA));

        assertSame(ErrorCode.PANORAMA_FILE_REQUIRED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void uploadMediaRejectsFileOverTenMegabytesBeforeQuotaReservation() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.mp4",
                "video/mp4",
                new byte[10 * 1024 * 1024 + 1]);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT));

        assertSame(ErrorCode.FILE_TOO_LARGE, exception.getErrorCode());
        verify(storageService, never()).reserveUsage(any(), any(Long.class));
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void uploadMediaRejectsRequestWithPendingCancellation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "intro.mp4", "video/mp4", "video".getBytes());
        request.setCancellationStatus(DesignRequestCancellationStatus.REQUESTED);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT));

        assertSame(ErrorCode.DESIGN_CANCELLATION_PENDING, exception.getErrorCode());
        verify(storageService, never()).reserveUsage(any(), any(Long.class));
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void uploadMediaCleansCloudAssetWhenRepositorySaveFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "intro.mp4", "video/mp4", "video".getBytes());
        CloudinaryResponse upload = mediaUpload();
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(cloudService.uploadToFolder(file, "design-draft-media-attachment")).thenReturn(upload);
        when(assetRepository.save(any(DesignDraftAsset.class))).thenThrow(new IllegalStateException("database down"));

        assertThrows(
                IllegalStateException.class,
                () -> service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT));

        verify(cloudService).delete("design-media/intro", "video");
    }

    @Test
    void uploadMediaCleansCloudAssetWhenTransactionRollsBackAfterMethodReturns() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "intro.mp4", "video/mp4", "video".getBytes());
        CloudinaryResponse upload = mediaUpload();
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(cloudService.uploadToFolder(file, "design-draft-media-attachment")).thenReturn(upload);
        when(assetRepository.save(any(DesignDraftAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.uploadAsset(designer, request.getId(), file, DesignDraftAssetType.MEDIA_ATTACHMENT);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(cloudService).delete("design-media/intro", "video");
    }

    @Test
    void releaseAssetRejectsAssetReferencedBySubmittedDraft() {
        DesignDraftAsset asset = asset("panorama/in-use");
        request.getDrafts().add(DesignDraft.builder().versionNumber(0).designRequest(request).build());
        DesignDraft draft = DesignDraft.builder().versionNumber(1).designRequest(request).build();
        draft.getPanoramas().add(DesignDraftPanorama.builder()
                .draft(draft)
                .clientKey("p1")
                .name("Pano")
                .imageUrl(asset.getUrl())
                .imageKey(asset.getPublicId())
                .orderIndex(0)
                .build());
        request.getDrafts().add(draft);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.releaseAsset(designer, request.getId(), asset.getId()));

        assertSame(ErrorCode.DESIGN_DRAFT_MEDIA_IN_USE, exception.getErrorCode());
        verify(cloudService, never()).delete(any(), any());
    }

    @Test
    void releaseAssetDetachesOnlyTargetDraftMediaAndDeletesStagingFile() {
        DesignDraftAsset asset = asset("design-media/unused");
        asset.setAssetType(DesignDraftAssetType.MEDIA_ATTACHMENT);
        asset.setQuotaState(DesignDraftAssetQuotaState.STAGED);
        DesignDraftAsset retainedAsset = asset("design-media/retained");
        retainedAsset.setAssetType(DesignDraftAssetType.MEDIA_ATTACHMENT);
        retainedAsset.setQuotaState(DesignDraftAssetQuotaState.STAGED);
        DesignDraft draft = DesignDraft.builder().versionNumber(0).designRequest(request).build();
        DesignDraftMediaAsset media = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(asset)
                .build();
        DesignDraftMediaAsset retainedMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(retainedAsset)
                .build();
        draft.getMediaAssets().addAll(List.of(media, retainedMedia));
        request.getDrafts().add(draft);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));
        when(boothDesignService.getPanoramaImageKeys(request.getBooth().getId())).thenReturn(Set.of());

        service.releaseAsset(designer, request.getId(), asset.getId());

        assertEquals(List.of(retainedMedia), draft.getMediaAssets());
        verify(draftMediaAssetRepository).deleteAll(List.of(media));
        verify(assetRepository).delete(asset);
        verify(assetRepository, never()).delete(retainedAsset);
        verify(assetReferenceService).scheduleCleanup(asset.getPublicId(), "image");
    }

    @Test
    void releaseAssetRejectsMediaReferencedByWorkingDraftHotspot() {
        DesignDraftAsset asset = asset("design-media/in-use");
        asset.setAssetType(DesignDraftAssetType.MEDIA_ATTACHMENT);
        asset.setQuotaState(DesignDraftAssetQuotaState.STAGED);
        DesignDraft draft = DesignDraft.builder().versionNumber(0).designRequest(request).build();
        DesignDraftMediaAsset media = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(asset)
                .build();
        draft.getMediaAssets().add(media);
        DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                .draft(draft)
                .clientKey("p1")
                .name("Pano")
                .imageUrl("https://cdn.example/panorama")
                .imageKey("panorama/in-use")
                .orderIndex(0)
                .build();
        panorama.getHotspots().add(DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .designDraftMediaAsset(media)
                .build());
        draft.getPanoramas().add(panorama);
        request.getDrafts().add(draft);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.releaseAsset(designer, request.getId(), asset.getId()));

        assertSame(ErrorCode.DESIGN_DRAFT_MEDIA_IN_USE, exception.getErrorCode());
        assertEquals(List.of(media), draft.getMediaAssets());
        verify(assetRepository, never()).delete(asset);
        verifyNoInteractions(assetReferenceService);
    }

    @Test
    void cleanupAfterApprovalDeletesAssetsNotUsedByAppliedBooth() {
        DesignDraftAsset used = asset("panorama/used");
        DesignDraftAsset unused = asset("panorama/unused");
        when(assetRepository.findByDesignRequestBoothId(request.getBooth().getId()))
                .thenReturn(List.of(used, unused));
        when(boothDesignService.getPanoramaImageKeys(request.getBooth().getId()))
                .thenReturn(Set.of("panorama/used"));

        service.cleanupAfterApproval(request);

        verify(assetRepository).delete(unused);
        verify(storageService).deductUsage(company, unused.getFileSize());
        verify(assetReferenceService).scheduleCleanup("panorama/unused", "image");
        verify(assetRepository, never()).delete(used);
    }

    @Test
    void cleanupAfterApprovalKeepsAssetReferencedByRetainedDraftVersion() {
        DesignDraftAsset retained = asset("panorama/retained-version");
        when(assetRepository.findByDesignRequestBoothId(request.getBooth().getId()))
                .thenReturn(List.of(retained));
        when(boothDesignService.getPanoramaImageKeys(request.getBooth().getId()))
                .thenReturn(Set.of());
        when(assetReferenceService.isReferenced(retained.getPublicId())).thenReturn(true);

        service.cleanupAfterApproval(request);

        verify(assetRepository, never()).delete(retained);
        verify(storageService, never()).deductUsage(company, retained.getFileSize());
    }

    @Test
    void getAssetsReturnsOnlyAssetsFromAssignedRequest() {
        DesignDraftAsset asset = asset("panorama/available");
        when(workspaceService.getAssignedRequest(designer, request.getId())).thenReturn(request);
        PageRequest pageable = PageRequest.of(0, 20);
        when(assetRepository.findByDesignRequestId(request.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(asset), pageable, 1));

        PageResponse<DesignDraftAssetResponseDTO> assets = service.getAssets(designer, request.getId(), pageable);

        assertEquals(1, assets.getContent().size());
        assertEquals(asset.getId(), assets.getContent().get(0).getId());
    }

    @Test
    void renameMediaAssetUpdatesDisplayNameOnly() {
        DesignDraftAsset asset = asset("design-media/intro");
        asset.setAssetType(DesignDraftAssetType.MEDIA_ATTACHMENT);
        DesignDraft draft = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .build();
        draft.getMediaAssets().add(com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(asset)
                .title("Old title")
                .build());
        request.getDrafts().add(draft);
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);

        DesignDraftAssetResponseDTO response = service.renameAsset(designer, request.getId(), asset.getId(),
                "  Welcome video  ");

        assertEquals("Welcome video", response.getFileName());
        assertEquals("Welcome video", draft.getMediaAssets().get(0).getTitle());
        assertEquals("https://cdn.example/design-media/intro", asset.getUrl());
        assertEquals("design-media/intro", asset.getPublicId());
        assertEquals("image/jpeg", asset.getMimeType());
    }

    @Test
    void renameMediaAssetRejectsBlankOrOverlongName() {
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);

        assertSame(
                ErrorCode.DESIGN_DRAFT_ASSET_NAME_INVALID,
                assertThrows(
                        AppException.class,
                        () -> service.renameAsset(designer, request.getId(), UUID.randomUUID(), "  "))
                        .getErrorCode());
        assertSame(
                ErrorCode.DESIGN_DRAFT_ASSET_NAME_INVALID,
                assertThrows(
                        AppException.class,
                        () -> service.renameAsset(
                                designer,
                                request.getId(),
                                UUID.randomUUID(),
                                "x".repeat(256)))
                        .getErrorCode());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void renameAssetRejectsNonMediaAsset() {
        DesignDraftAsset asset = asset("panorama/pano");
        asset.setAssetType(DesignDraftAssetType.PANORAMA);
        request.getDrafts().add(DesignDraft.builder().versionNumber(0).designRequest(request).build());
        when(workspaceService.getAssignedRequestForUpdate(designer, request.getId())).thenReturn(request);
        when(assetRepository.findByIdAndDesignRequestId(asset.getId(), request.getId()))
                .thenReturn(Optional.of(asset));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.renameAsset(designer, request.getId(), asset.getId(), "New name"));

        assertSame(ErrorCode.DESIGN_DRAFT_ASSET_TYPE_INVALID, exception.getErrorCode());
        verify(assetRepository, never()).save(any());
    }

    private DesignDraftAsset asset(String publicId) {
        return DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .uploadedBy(designer)
                .url("https://cdn.example/" + publicId)
                .publicId(publicId)
                .fileName("pano.jpg")
                .mimeType("image/jpeg")
                .fileSize(5L)
                .quotaState(DesignDraftAssetQuotaState.CHARGED)
                .build();
    }

    private CloudinaryResponse mediaUpload() {
        return CloudinaryResponse.builder()
                .url("https://cdn.example/intro.mp4")
                .publicId("design-media/intro")
                .fileName("intro.mp4")
                .fileSize(5L)
                .fileType("video/mp4")
                .build();
    }
}
