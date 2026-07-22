package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftAssetResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
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
    void uploadPanoramaChargesExhibitorCompanyQuota() {
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
        verify(storageService).checkQuota(company, 5L);
        verify(storageService).addUsage(company, 5L);
    }

    @Test
    void releaseAssetRejectsAssetReferencedBySubmittedDraft() {
        DesignDraftAsset asset = asset("panorama/in-use");
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

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
        verify(cloudService, never()).delete(any(), any());
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
                .build();
    }
}
