package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.BoothDesignService.PanoramaDesign;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;

import com.example.vex360.features.designrequest.dtos.request.AssignDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.ApproveDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.events.DesignRequestStatusChangedEvent;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.designrequest.services.DesignRequestBaselineService;
import com.example.vex360.features.designrequest.services.DesignDraftSettingsService;
import com.example.vex360.features.designrequest.services.DesignDraftCloneService;
import com.example.vex360.features.designrequest.services.DesignDraftRetentionService;
import com.example.vex360.features.designrequest.services.DesignDraftGraphValidator;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetQuotaState;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.dtos.request.RejectDesignDraftRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestServiceUnitTest {
    @Mock
    private DesignRequestRepository designRequestRepository;
    @Mock
    private DesignDraftRepository designDraftRepository;
    @Mock
    private DesignDraftAssetRepository designDraftAssetRepository;
    @Mock
    private BoothDesignService boothDesignService;
    @Mock
    private CompanyService companyService;
    @Mock
    private UserService userService;
    @Mock
    private ProductService productService;
    @Mock
    private DesignDraftAssetService designDraftAssetService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private HotspotRepository hotspotRepository;
    @Mock
    private DesignRequestProductService requestProductService;
    @Mock
    private DesignRequestBaselineService baselineService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DesignDraftSettingsService draftSettingsService;
    @Mock
    private DesignDraftCloneService draftCloneService;
    @Mock
    private DesignDraftRetentionService draftRetentionService;
    @Mock
    private DesignDraftGraphValidator draftGraphValidator;
    @Mock
    private DesignDraftBenefitGuardService draftBenefitGuardService;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private CompanyStorageService storageService;

    private DesignRequestService service;
    private User exhibitor;
    private User designer;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setup() {
        DesignRequestEligibilityService eligibilityService = new DesignRequestEligibilityService(
                panoramaRepository, hotspotRepository, designRequestRepository);
        service = new DesignRequestService(
                designRequestRepository,
                designDraftRepository,
                designDraftAssetRepository,
                mediaAssetRepository,
                storageService,
                Mappers.getMapper(DesignRequestMapper.class),
                boothDesignService,
                companyService,
                userService,
                productService,
                eligibilityService,
                requestProductService,
                baselineService,
                designDraftAssetService,
                draftSettingsService,
                draftCloneService,
                draftRetentionService,
                draftGraphValidator,
                draftBenefitGuardService,
                eventPublisher,
                userRepository);

        exhibitor = User.builder()
                .id(UUID.randomUUID())
                .email("exh@example.com")
                .role(Role.EXHIBITOR)
                .status(UserStatus.ACTIVE)
                .build();
        designer = User.builder()
                .id(UUID.randomUUID())
                .email("designer@example.com")
                .fullName("Designer")
                .role(Role.DESIGNER)
                .status(UserStatus.ACTIVE)
                .build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitor).name("Exh Co").build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .company(company)
                .createdBy(exhibitor)
                .name("Main booth")
                .isTemplate(false)
                .build();
    }

    @Test
    void getRequestsForAdminNormalizesKeywordAndMapsSortAliases() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by(
                Sort.Order.desc("boothName"),
                Sort.Order.asc("status")));
        Pageable mappedPageable = PageRequest.of(2, 5, Sort.by(
                Sort.Order.desc("booth.name"),
                Sort.Order.asc("status")));
        when(designRequestRepository.searchForAdmin(
                "Expo", DesignRequestStatus.PENDING, mappedPageable))
                .thenReturn(Page.empty(mappedPageable));

        PageResponse<?> response = service.getRequestsForAdmin(
                " Expo ", DesignRequestStatus.PENDING, pageable);

        assertEquals(2, response.getPage());
        assertEquals(5, response.getSize());
    }

    @Test
    void getRequestsForExhibitorNormalizesKeywordAndKeepsRequestedSort() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("status")));
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.searchForCompany(
                company.getId(), "Expo", DesignRequestStatus.PENDING, pageable))
                .thenReturn(Page.empty(pageable));

        PageResponse<?> response = service.getRequestsForExhibitor(
                exhibitor, "  Expo  ", DesignRequestStatus.PENDING, pageable);

        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        verify(designRequestRepository).searchForCompany(
                company.getId(), "Expo", DesignRequestStatus.PENDING, pageable);
    }

    @Test
    void createRequestMovesDraftBoothToDesigning() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothDesignService.getCompanyBoothForUpdate(booth.getId(), company.getId())).thenReturn(booth);
        when(designRequestRepository.countByBoothIdAndQuotaChargedTrue(booth.getId())).thenReturn(0L);
        when(designRequestRepository.sumReviewCountByBoothId(booth.getId())).thenReturn(0L);
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createRequest(exhibitor, new CreateDesignRequest(booth.getId(), "Need design"));

        assertEquals(BoothStatus.DESIGN_REQUEST_PENDING, booth.getStatus());
    }

    @Test
    void createRequestRejectsBoothAlreadyBeingDesigned() {
        booth.setStatus(BoothStatus.DESIGNING);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothDesignService.getCompanyBoothForUpdate(booth.getId(), company.getId())).thenReturn(booth);

        AppException exception = assertThrows(AppException.class,
                () -> service.createRequest(exhibitor, new CreateDesignRequest(booth.getId(), "Need design")));

        assertSame(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void createRequestThrowsWhenBoothActionQuotaIsFull() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothDesignService.getCompanyBoothForUpdate(booth.getId(), company.getId())).thenReturn(booth);
        when(designRequestRepository.countByBoothIdAndQuotaChargedTrue(booth.getId())).thenReturn(2L);
        when(designRequestRepository.sumReviewCountByBoothId(booth.getId())).thenReturn(1L);

        AppException exception = assertThrows(AppException.class,
                () -> service.createRequest(exhibitor, new CreateDesignRequest(booth.getId(), "Need design")));

        assertSame(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void cancelPendingRequestMovesBoothBackToDraft() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = pendingRequest(requestId);
        booth.setStatus(BoothStatus.DESIGNING);

        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelRequest(exhibitor, requestId);

        assertEquals(BoothStatus.DRAFT, booth.getStatus());
        assertEquals(DesignRequestStatus.CANCELLED, request.getStatus());
    }

    @Test
    void assignRequestThrowsWhenDesignerAlreadyHasThreeActiveRequests() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = pendingRequest(requestId);

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityByIdForUpdate(designer.getId())).thenReturn(designer);
        when(designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                eq(designer.getId()),
                eq(DesignRequestRepository.SLOT_OCCUPYING_STATUSES))).thenReturn(3L);

        AppException exception = assertThrows(AppException.class,
                () -> service.assignRequest(requestId, new AssignDesignRequest(designer.getId())));

        assertSame(ErrorCode.DESIGNER_WORKLOAD_EXCEEDED, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void submittedDraftStillConsumesDesignerSlot() {
        assertTrue(DesignRequestRepository.SLOT_OCCUPYING_STATUSES.contains(DesignRequestStatus.DRAFT_SUBMITTED));
    }

    @Test
    void assignRequestThrowsWhenAssignedUserIsNotDesigner() {
        UUID requestId = UUID.randomUUID();
        User visitor = User.builder().id(UUID.randomUUID()).role(Role.VISITOR).build();

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest(requestId)));
        when(userService.getUserEntityByIdForUpdate(visitor.getId())).thenReturn(visitor);

        AppException exception = assertThrows(AppException.class,
                () -> service.assignRequest(requestId, new AssignDesignRequest(visitor.getId())));

        assertSame(ErrorCode.INVALID_DESIGNER, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void saveWorkingDraftThrowsWhenProductDoesNotBelongToCompany() {
        UUID requestId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        SubmitDesignDraftRequest draftRequest = new SubmitDesignDraftRequest(
                "Draft",
                List.of(new SubmitDesignDraftPanoramaRequest(
                        "p1",
                        "Entrance",
                        "https://cdn.example.com/pano.jpg",
                        "pano-key",
                        0,
                        true,
                        List.of(new SubmitDesignDraftHotspotRequest(
                                HotspotType.PRODUCT,
                                "Product",
                                1.0,
                                2.0,
                                3.0,
                                null,
                                productId,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)))));

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(productService.getProductForCompany(productId, company))
                .thenThrow(new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        AppException exception = assertThrows(AppException.class,
                () -> service.saveWorkingDraft(designer, requestId, draftRequest));

        assertSame(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void saveWorkingDraftReplacesVersionZeroAndKeepsRequestAssigned() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        DesignDraft oldWorking = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .build();
        request.getDrafts().add(oldWorking);
        SubmitDesignDraftRequest draftRequest = simpleDraftRequest();

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveWorkingDraft(designer, requestId, draftRequest);

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        assertEquals(DesignRequestStatus.ASSIGNED, request.getStatus());
        verify(draftGraphValidator).validateWorkingGraph(eq(request), any(DesignDraft.class));
        verify(draftBenefitGuardService).assertMutationAllowed(
                eq(request),
                any(),
                any(DesignDraft.class));
        verify(designDraftAssetService).cleanupUnreferencedAssets(request);
    }

    @Test
    void saveWorkingDraftAllowsTemporaryZeroPanoramaState() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        DesignDraft working = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .build();
        request.getDrafts().add(working);
        SubmitDesignDraftRequest draftRequest = new SubmitDesignDraftRequest("empty", List.of());

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveWorkingDraft(designer, requestId, draftRequest);

        assertTrue(working.getPanoramas().isEmpty());
        verify(draftGraphValidator).validateWorkingGraph(eq(request), any(DesignDraft.class));
    }

    @Test
    void submitWorkingDraftTurnsVersionZeroIntoNextImmutableVersion() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.getDrafts().add(DesignDraft.builder()
                .designRequest(request)
                .versionNumber(1)
                .build());
        DesignDraft working = DesignDraft.builder()
                .designRequest(request)
                .versionNumber(0)
                .build();
        request.getDrafts().add(working);

        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.submitWorkingDraft(designer, requestId);

        assertEquals(2, working.getVersionNumber());
        assertEquals(DesignRequestStatus.DRAFT_SUBMITTED, request.getStatus());
        verify(draftGraphValidator).validateForSubmission(request, working);
        verify(draftBenefitGuardService).assertWithinSubmissionLimits(request, working);
        verify(eventPublisher).publishEvent(any(DesignRequestStatusChangedEvent.class));
        verify(userService, never()).getUserEntityByIdForUpdate(designer.getId());
    }

    @Test
    void rejectDraftCountsAgainstBoothActionQuota() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);

        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.countByBoothIdAndQuotaChargedTrue(booth.getId())).thenReturn(1L);
        when(designRequestRepository.sumReviewCountByBoothId(booth.getId())).thenReturn(2L);

        AppException exception = assertThrows(AppException.class,
                () -> service.rejectDraft(exhibitor, requestId, null));

        assertSame(ErrorCode.DESIGN_REQUEST_QUOTA_EXCEEDED, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void rejectDraftRequiresReviewNote() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(AppException.class,
                () -> service.rejectDraft(exhibitor, requestId, new RejectDesignDraftRequest(" ")));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    @Test
    void rejectDraftReturnsToRevisionWithoutAcquiringAnotherSlot() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.rejectDraft(exhibitor, requestId, new RejectDesignDraftRequest("Move entrance"));

        assertEquals(DesignRequestStatus.REVISION_REQUESTED, request.getStatus());
        verify(draftCloneService).cloneLatestSubmittedToWorking(request);
        verify(userService, never()).getUserEntityByIdForUpdate(designer.getId());
    }

    @Test
    void cancelPendingRequestRefundsCreateAction() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = pendingRequest(requestId);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelRequest(exhibitor, requestId);

        assertEquals(false, request.getQuotaCharged());
    }

    @Test
    void cancellationAfterAssignmentWaitsForAdminDecision() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCancellation(exhibitor, requestId, "Priorities changed");

        assertEquals(DesignRequestStatus.ASSIGNED, request.getStatus());
        assertEquals(DesignRequestCancellationStatus.REQUESTED, request.getCancellationStatus());
    }

    @Test
    void approveCancellationClearsDraftsAndReleasesStagingAssets() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setCancellationStatus(DesignRequestCancellationStatus.REQUESTED);
        request.getDrafts().add(DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(0)
                .build());
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.decideCancellation(requestId, true, "Approved");

        assertEquals(DesignRequestStatus.CANCELLED, request.getStatus());
        assertTrue(request.getDrafts().isEmpty());
        verify(designDraftRepository).flush();
        verify(designDraftAssetService).cleanupAfterApproval(request);
    }

    @Test
    void approveDraftAppliesDraftPanoramaAndHotspotToBooth() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Product A")
                .sku("SKU-A")
                .status(ProductStatus.ACTIVE)
                .build();
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Video")
                .type(MediaAssetType.VIDEO)
                .build();
        DesignDraft draft = draft(request, product, mediaAsset);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveDraft(exhibitor, requestId);

        ArgumentCaptor<List<PanoramaDesign>> designCaptor = ArgumentCaptor.forClass(List.class);
        verify(boothDesignService).replaceBoothContent(eq(booth), designCaptor.capture());
        PanoramaDesign appliedPanorama = designCaptor.getValue().get(0);
        assertEquals("Entrance", appliedPanorama.name());
        assertEquals(HotspotType.MEDIA, appliedPanorama.hotspots().get(0).type());
        assertSame(mediaAsset, appliedPanorama.hotspots().get(0).mediaAsset());
        assertEquals(DesignRequestStatus.APPROVED, request.getStatus());
        assertEquals(BoothStatus.DRAFT, booth.getStatus());
        verify(draftRetentionService).retainApprovedDraft(request, draft);
        verify(designDraftAssetService).cleanupAfterApproval(request);
    }

    @Test
    void approveDraft_WithNoAcceptedIds_DoesNotPromoteSubmittedMedia() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft draft = draft(request, null, null);
        DesignDraftMediaAsset submittedMedia = submittedMedia(draft, "image/jpeg", 12L);
        draft.getMediaAssets().add(submittedMedia);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveDraft(exhibitor, requestId, new ApproveDesignDraftRequest(List.of()));

        assertTrue(draft.getMediaAssets().isEmpty());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        verify(storageService, never()).promoteReservedUsage(any(), any(Long.class));
    }

    @Test
    void approveDraft_IgnoresDeprecatedAcceptedMediaIds() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft draft = draft(request, null, null);
        draft.getMediaAssets().add(submittedMedia(draft, "image/jpeg", 12L));
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveDraft(
                exhibitor,
                requestId,
                new ApproveDesignDraftRequest(List.of(UUID.randomUUID())));

        assertTrue(draft.getMediaAssets().isEmpty());
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void approveDraft_PromotesOnlyReferencedStagedMedia() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft draft = draft(request, null, null);
        DesignDraftMediaAsset accepted = submittedMedia(draft, "video/mp4", 20L);
        DesignDraftMediaAsset rejected = submittedMedia(draft, "image/jpeg", 12L);
        draft.getMediaAssets().addAll(List.of(accepted, rejected));
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        panorama.getHotspots().get(0).setDesignDraftMediaAsset(accepted);
        panorama.getHotspots().add(DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .designDraftMediaAsset(accepted)
                .build());
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset media = invocation.getArgument(0);
            media.setId(UUID.randomUUID());
            return media;
        });
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveDraft(
                exhibitor,
                requestId,
                new ApproveDesignDraftRequest(List.of(rejected.getId())));

        assertEquals(List.of(accepted), draft.getMediaAssets());
        assertEquals(DesignDraftAssetQuotaState.PROMOTED, accepted.getAsset().getQuotaState());
        verify(storageService).reconcileUsage(company, 0L, 20L, 0L);
        verify(storageService, never()).promoteReservedUsage(company, 20L);
        ArgumentCaptor<MediaAsset> mediaCaptor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(mediaCaptor.capture());
        assertEquals(MediaAssetType.VIDEO, mediaCaptor.getValue().getType());
        assertSame(
                panorama.getHotspots().get(0).getMediaAsset(),
                panorama.getHotspots().get(1).getMediaAsset());
    }

    @Test
    void approveDraft_DoesNotPromoteOrApplyBoothWhenQuotaIsExceeded() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft draft = draft(request, null, null);
        DesignDraftMediaAsset staged = submittedMedia(draft, "image/jpeg", 30L);
        draft.getMediaAssets().add(staged);
        draft.getPanoramas().get(0).getHotspots().get(0).setDesignDraftMediaAsset(staged);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        doThrow(new AppException(ErrorCode.STORAGE_QUOTA_EXCEEDED))
                .when(storageService)
                .reconcileUsage(company, 0L, 30L, 0L);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.approveDraft(exhibitor, requestId));

        assertSame(ErrorCode.STORAGE_QUOTA_EXCEEDED, exception.getErrorCode());
        assertEquals(DesignRequestStatus.DRAFT_SUBMITTED, request.getStatus());
        verify(mediaAssetRepository, never()).save(any());
        verify(boothDesignService, never()).replaceBoothContent(any(), any());
    }

    @Test
    void approveDraft_ExcludesPanoramaFromQuotaReconciliation() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        request.getBooth().getPanoramas().add(Panorama.builder()
                .booth(request.getBooth())
                .imageKey("panorama/old")
                .fileSize(100L)
                .isTemplateDerived(false)
                .build());
        DesignDraft draft = draft(request, null, null);
        DesignDraftAsset replacement = DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .publicId("pano-key")
                .fileSize(110L)
                .assetType(DesignDraftAssetType.PANORAMA)
                .assetSource(DesignDraftAssetSource.UPLOADED)
                .quotaState(DesignDraftAssetQuotaState.STAGED)
                .build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));
        when(designDraftAssetRepository.findByDesignRequestId(requestId)).thenReturn(List.of(replacement));
        when(designRequestRepository.save(any(DesignRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.approveDraft(exhibitor, requestId);

        verify(storageService).reconcileUsage(company, 0L, 0L, 0L);
        assertEquals(DesignDraftAssetQuotaState.PROMOTED, replacement.getQuotaState());
    }

    @Test
    void forceCleanupRejectsActiveRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.cleanupTerminalAssets(requestId));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
        verify(designDraftAssetService, never()).cleanupAfterApproval(any());
    }

    private DesignRequest pendingRequest(UUID id) {
        booth.setStatus(BoothStatus.DESIGN_REQUEST_PENDING);
        return DesignRequest.builder()
                .id(id)
                .booth(booth)
                .company(company)
                .requestedBy(exhibitor)
                .status(DesignRequestStatus.PENDING)
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .reviewCount(0)
                .build();
    }

    private DesignRequest assignedRequest(UUID id) {
        DesignRequest request = pendingRequest(id);
        request.setAssignedDesigner(designer);
        request.setStatus(DesignRequestStatus.ASSIGNED);
        return request;
    }

    private DesignDraftMediaAsset submittedMedia(DesignDraft draft, String mimeType, long size) {
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(draft.getDesignRequest())
                .url("https://cdn.example/media/" + UUID.randomUUID())
                .publicId("design-media/" + UUID.randomUUID())
                .fileName("attachment")
                .mimeType(mimeType)
                .fileSize(size)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .quotaState(DesignDraftAssetQuotaState.STAGED)
                .build();
        return DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(asset)
                .title("Attachment")
                .sortOrder(draft.getMediaAssets().size())
                .build();
    }

    private SubmitDesignDraftRequest simpleDraftRequest() {
        return new SubmitDesignDraftRequest(
                "Working",
                List.of(new SubmitDesignDraftPanoramaRequest(
                        "p1",
                        "Entrance",
                        "https://cdn.example.com/pano.jpg",
                        "panorama/pano",
                        0,
                        true,
                        List.of())));
    }

    private DesignDraft draft(DesignRequest request, Product product, MediaAsset mediaAsset) {
        DesignDraft draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(1)
                .build();
        DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .clientKey("p1")
                .name("Entrance")
                .imageUrl("https://cdn.example.com/pano.jpg")
                .imageKey("pano-key")
                .orderIndex(0)
                .isDefault(true)
                .build();
        DesignDraftHotspot hotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .type(HotspotType.MEDIA)
                .name("Video")
                .mediaAsset(mediaAsset)
                .product(product)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
        panorama.getHotspots().add(hotspot);
        draft.getPanoramas().add(panorama);
        return draft;
    }
}
