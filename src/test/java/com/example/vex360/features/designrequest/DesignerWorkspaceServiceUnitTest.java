package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.designrequest.dtos.response.DesignerWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftContentAssembler;
import com.example.vex360.features.designrequest.services.DesignDraftDiffService;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.designrequest.services.DesignDraftStorageMetricsService;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.dtos.PageResponse;

@ExtendWith(MockitoExtension.class)
class DesignerWorkspaceServiceUnitTest {
    @Mock
    DesignRequestRepository designRequestRepository;
    @Mock
    BoothMapper boothMapper;
    @Mock
    DesignRequestProductRepository requestProductRepository;
    @Mock
    DesignRequestMediaAssetRepository requestMediaAssetRepository;
    @Mock
    ProductMapper productMapper;
    @Mock
    CompanyService companyService;
    @Mock
    CompanyStorageService storageService;
    @Mock
    DesignRequestEligibilityService eligibilityService;
    @Mock
    DesignDraftBenefitGuardService benefitGuardService;
    @Mock
    DesignDraftStorageMetricsService storageMetricsService;
    @Mock
    DesignRequestMapper designRequestMapper;
    @Mock
    DesignDraftContentAssembler designDraftContentAssembler;
    @Mock
    DesignDraftDiffService designDraftDiffService;
    @Mock
    DesignRequestLifecyclePolicy lifecyclePolicy;

    private DesignerWorkspaceService service;
    private User designer;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignerWorkspaceService(
                designRequestRepository,
                boothMapper,
                requestProductRepository,
                requestMediaAssetRepository,
                productMapper,
                companyService,
                storageService,
                storageMetricsService,
                eligibilityService,
                benefitGuardService,
                designRequestMapper,
                designDraftContentAssembler,
                designDraftDiffService,
                lifecyclePolicy);
        designer = User.builder().id(UUID.randomUUID()).build();
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .storageUsedBytes(0L)
                .storageReservedBytes(0L)
                .storageQuotaBytes(100L)
                .build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).name("Booth").build())
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .contactEmail("contact@example.com")
                .contactPhone("0912345678")
                .build();
    }

    @Test
    void assignedRequestForUpdateAppliesLifecyclePolicy() {
        when(designRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        doThrow(new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED))
                .when(lifecyclePolicy).assertCanContinue(request);

        AppException exception = assertThrows(AppException.class,
                () -> service.getAssignedRequestForUpdate(designer, request.getId()));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
    }

    @Test
    void getWorkspaceReturnsAssignedRequestAndBooth() {
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        DesignerWorkspaceResponseDTO response = service.getWorkspace(designer, request.getId());

        assertEquals(request.getId(), response.getRequestId());
        assertEquals(DesignRequestStatus.ASSIGNED, response.getStatus());
        assertEquals("contact@example.com", response.getContactEmail());
        assertEquals("0912345678", response.getContactPhone());
        assertEquals(0, response.getRequiredMediaAssetCount());
        assertEquals(0, response.getOptionalMediaAssetCount());
    }

    @Test
    void getWorkspaceRejectsAnotherDesigner() {
        User anotherDesigner = User.builder().id(UUID.randomUUID()).build();
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getWorkspace(anotherDesigner, request.getId()));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void getHistoricalDraftPreviewRejectsVersionZero() {
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(request.getCompany());
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getHistoricalDraftPreview(exhibitor, request.getId(), 0));

        assertSame(ErrorCode.DESIGN_DRAFT_VERSION_INVALID, exception.getErrorCode());
    }

    @Test
    void getProductsRejectsUnassignedDesigner() {
        User anotherDesigner = User.builder().id(UUID.randomUUID()).build();
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getProducts(anotherDesigner, request.getId(), null, null,
                        Pageable.unpaged()));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void getMediaAssetsUsesRequestAllowlistPaginationAndImageFilter() {
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(request.getCompany())
                .type(MediaAssetType.IMAGE)
                .build();
        DesignRequestMediaAsset allowlistItem = DesignRequestMediaAsset.builder()
                .designRequest(request)
                .mediaAsset(mediaAsset)
                .requiredFromBaseline(false)
                .build();
        Pageable pageable = PageRequest.of(1, 5);
        MediaAssetResponseDTO mapped = new MediaAssetResponseDTO();
        mapped.setId(mediaAsset.getId());
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestMediaAssetRepository.searchAllowedMediaAssets(
                request.getId(), MediaAssetType.IMAGE, pageable))
                .thenReturn(new PageImpl<>(List.of(allowlistItem), pageable, 6));
        when(boothMapper.toMediaAssetResponseDTO(mediaAsset)).thenReturn(mapped);

        PageResponse<MediaAssetResponseDTO> response = service.getMediaAssets(
                designer, request.getId(), " image ", pageable);

        assertEquals(1, response.getPage());
        assertEquals(5, response.getSize());
        assertEquals(6, response.getTotalElements());
        assertEquals(mediaAsset.getId(), response.getContent().get(0).getId());
        verify(requestMediaAssetRepository).searchAllowedMediaAssets(
                request.getId(), MediaAssetType.IMAGE, pageable);
    }

    @Test
    void getMediaAssetsRejectsInvalidFilter() {
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getMediaAssets(designer, request.getId(), "audio", Pageable.unpaged()));

        assertSame(ErrorCode.DESIGN_MEDIA_ASSET_FILTER_INVALID, exception.getErrorCode());
    }
}
