package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.ExhibitorDesignReviewWorkspaceResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftContentAssembler;
import com.example.vex360.features.designrequest.services.DesignDraftDiffService;
import com.example.vex360.features.designrequest.services.DesignDraftStorageMetricsService;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignerWorkspaceServiceAdditionalUnitTest {

    @Mock
    private DesignRequestRepository designRequestRepository;
    @Mock
    private BoothMapper boothMapper;
    @Mock
    private DesignRequestProductRepository requestProductRepository;
    @Mock
    private DesignRequestMediaAssetRepository requestMediaAssetRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CompanyService companyService;
    @Mock
    private CompanyStorageService storageService;
    @Mock
    private DesignDraftStorageMetricsService storageMetricsService;
    @Mock
    private DesignRequestEligibilityService eligibilityService;
    @Mock
    private DesignDraftBenefitGuardService benefitGuardService;
    @Mock
    private DesignRequestMapper designRequestMapper;
    @Mock
    private DesignDraftContentAssembler designDraftContentAssembler;
    @Mock
    private DesignDraftDiffService designDraftDiffService;
    @Mock
    private DesignRequestLifecyclePolicy lifecyclePolicy;

    private DesignerWorkspaceService service;
    private User designer;
    private Company company;
    private Booth booth;
    private DesignRequest request;

    @BeforeEach
    void setUp() {
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
        company = Company.builder().id(UUID.randomUUID()).build();
        booth = Booth.builder().id(UUID.randomUUID()).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(booth)
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .build();
    }

    @Test
    void getProductsReturnsAllowedProductsAndNormalizesKeyword() {
        Product product = Product.builder().id(UUID.randomUUID()).company(company)
                .status(ProductStatus.ACTIVE).build();
        DesignRequestProduct item = DesignRequestProduct.builder()
                .designRequest(request)
                .product(product)
                .build();
        ProductResponseDTO mapped = new ProductResponseDTO();
        mapped.setId(product.getId());
        Pageable pageable = PageRequest.of(0, 5);
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestProductRepository.searchAllowedProducts(
                request.getId(), ProductStatus.ACTIVE, "chair", null, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));
        when(productMapper.toResponse(product)).thenReturn(mapped);

        PageResponse<ProductResponseDTO> response = service.getProducts(
                designer, request.getId(), "  chair  ", null, pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals(product.getId(), response.getContent().get(0).getId());
        verify(requestProductRepository).searchAllowedProducts(
                request.getId(), ProductStatus.ACTIVE, "chair", null, pageable);
    }

    @Test
    void getAssignedRequestForUpdateReturnsLockedAssignedRequest() {
        when(designRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        DesignRequest result = service.getAssignedRequestForUpdate(designer, request.getId());

        assertSame(request, result);
    }

    @Test
    void getAssignedRequestForUpdateRejectsUnauthenticatedUser() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.getAssignedRequestForUpdate(null, request.getId()));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void getReviewWorkspaceReturnsLatestSubmittedDraft() {
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        DesignDraft draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .versionNumber(1)
                .createdAt(Instant.now())
                .build();
        DesignRequest submitted = DesignRequest.builder()
                .id(request.getId())
                .company(company)
                .booth(booth)
                .status(DesignRequestStatus.DRAFT_SUBMITTED)
                .drafts(List.of(draft))
                .build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        ExhibitorDesignReviewWorkspaceResponseDTO response = service.getReviewWorkspace(
                exhibitor, submitted.getId());

        assertEquals(submitted.getId(), response.getRequestId());
        assertEquals(DesignRequestStatus.DRAFT_SUBMITTED, response.getStatus());
        assertEquals(1, response.getLatestSubmittedDraft().getVersionNumber());
        assertEquals(1, response.getSubmissionHistory().size());
        verify(designDraftDiffService).compareDraftWithBooth(draft, booth);
    }

    @Test
    void getReviewWorkspaceRejectsDifferentCompany() {
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        Company anotherCompany = Company.builder().id(UUID.randomUUID()).build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(anotherCompany);
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getReviewWorkspace(exhibitor, request.getId()));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void getReviewWorkspaceRejectsNonSubmittedRequest() {
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getReviewWorkspace(exhibitor, request.getId()));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
    }

    @Test
    void getReviewWorkspaceRejectsSubmittedRequestWithoutDraft() {
        User exhibitor = User.builder().id(UUID.randomUUID()).build();
        DesignRequest submitted = DesignRequest.builder()
                .id(request.getId())
                .company(company)
                .booth(booth)
                .status(DesignRequestStatus.DRAFT_SUBMITTED)
                .build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findById(submitted.getId())).thenReturn(Optional.of(submitted));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getReviewWorkspace(exhibitor, submitted.getId()));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }
}
