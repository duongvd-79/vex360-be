package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.request.AssignDesignRequest;
import com.example.vex360.features.designrequest.dtos.request.RejectDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftRequest;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestEligibilityResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftCloneService;
import com.example.vex360.features.designrequest.services.DesignDraftGraphValidator;
import com.example.vex360.features.designrequest.services.DesignDraftRetentionService;
import com.example.vex360.features.designrequest.services.DesignDraftSettingsService;
import com.example.vex360.features.designrequest.services.DesignRequestBaselineService;
import com.example.vex360.features.designrequest.services.DesignRequestEligibilityService;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.designrequest.services.DesignRequestMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.designrequest.services.DesignRequestService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestServiceAdditionalUnitTest {
    @Mock
    private DesignRequestRepository designRequestRepository;
    @Mock
    private DesignDraftRepository designDraftRepository;
    @Mock
    private DesignDraftAssetRepository designDraftAssetRepository;
    @Mock
    private CompanyStorageService storageService;
    @Mock
    private DesignRequestMapper designRequestMapper;
    @Mock
    private BoothDesignService boothDesignService;
    @Mock
    private ExhibitorMediaAssetService exhibitorMediaAssetService;
    @Mock
    private CompanyService companyService;
    @Mock
    private UserService userService;
    @Mock
    private ProductService productService;
    @Mock
    private DesignRequestEligibilityService eligibilityService;
    @Mock
    private DesignRequestProductService requestProductService;
    @Mock
    private DesignRequestMediaAssetService requestMediaAssetService;
    @Mock
    private DesignRequestBaselineService baselineService;
    @Mock
    private DesignDraftAssetService designDraftAssetService;
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
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DesignRequestLifecyclePolicy designRequestLifecyclePolicy;

    @InjectMocks
    private DesignRequestService service;

    private User exhibitor;
    private User designer;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setup() {
        exhibitor = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
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
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(exhibitor)
                .name("Company")
                .build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .company(company)
                .createdBy(exhibitor)
                .name("Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .build();
    }

    @Test
    void getEligibilityReturnsEvaluatedCompanyBooth() {
        DesignRequestEligibilityResponseDTO expected = new DesignRequestEligibilityResponseDTO(
                booth.getId(), DesignRequestMode.INITIAL_DESIGN, true, null, 3);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothDesignService.getCompanyBooth(booth.getId(), company.getId())).thenReturn(booth);
        when(eligibilityService.evaluate(booth)).thenReturn(expected);

        assertSame(expected, service.getEligibility(exhibitor, booth.getId()));
    }

    @Test
    void getRequestsForDesignerUsesAuthenticatedDesigner() {
        Pageable pageable = PageRequest.of(1, 5);
        when(designRequestRepository.searchForDesigner(
                designer.getId(), DesignRequestStatus.ASSIGNED, pageable))
                .thenReturn(Page.empty(pageable));

        PageResponse<?> response = service.getRequestsForDesigner(
                designer, DesignRequestStatus.ASSIGNED, pageable);

        assertEquals(1, response.getPage());
        assertEquals(5, response.getSize());
    }

    @Test
    void getAssignmentAnalyticsIncludesStatusCountsAndWorkload() {
        when(userService.findUsersByRoleAndStatus(Role.DESIGNER, UserStatus.ACTIVE))
                .thenReturn(List.of(designer));
        when(designRequestRepository.countFiltered(DesignRequestStatus.PENDING, DesignRequestMode.REDESIGN))
                .thenReturn(1L);
        when(designRequestRepository.countFilteredIn(
                DesignRequestRepository.SLOT_OCCUPYING_STATUSES, DesignRequestMode.REDESIGN))
                .thenReturn(2L);
        when(designRequestRepository.countFiltered(DesignRequestStatus.DRAFT_SUBMITTED, DesignRequestMode.REDESIGN))
                .thenReturn(3L);
        when(designRequestRepository.countFiltered(DesignRequestStatus.APPROVED, DesignRequestMode.REDESIGN))
                .thenReturn(4L);
        when(designRequestRepository.countFiltered(DesignRequestStatus.CANCELED, DesignRequestMode.REDESIGN))
                .thenReturn(5L);
        when(designRequestRepository.countDesignerFilteredIn(
                designer.getId(), DesignRequestRepository.SLOT_OCCUPYING_STATUSES, DesignRequestMode.REDESIGN))
                .thenReturn(2L);
        when(designRequestRepository.countDesignerFilteredIn(
                designer.getId(), List.of(DesignRequestStatus.DRAFT_SUBMITTED), DesignRequestMode.REDESIGN))
                .thenReturn(1L);

        var analytics = service.getAssignmentAnalytics(DesignRequestMode.REDESIGN);

        assertEquals(1L, analytics.getPendingRequests());
        assertEquals(2L, analytics.getWorkingRequests());
        assertEquals(3L, analytics.getWaitingReviewRequests());
        assertEquals(4L, analytics.getApprovedRequests());
        assertEquals(5L, analytics.getCanceledRequests());
        assertEquals(1, analytics.getDesignerWorkloads().size());
        assertEquals(1, analytics.getDesignerWorkloads().get(0).getAvailableSlots());
    }

    @Test
    void getAssignmentCandidatesSortsByAvailabilityAndClampsSlots() {
        User available = User.builder()
                .id(UUID.randomUUID())
                .email("available@example.com")
                .fullName("Zed")
                .role(Role.DESIGNER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userService.findUsersByRoleAndStatus(Role.DESIGNER, UserStatus.ACTIVE))
                .thenReturn(List.of(designer, available));
        when(designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                designer.getId(), DesignRequestRepository.SLOT_OCCUPYING_STATUSES)).thenReturn(4L);
        when(designRequestRepository.countByAssignedDesignerIdAndStatusIn(
                available.getId(), DesignRequestRepository.SLOT_OCCUPYING_STATUSES)).thenReturn(1L);

        var candidates = service.getAssignmentCandidates();

        assertEquals(List.of(available.getId(), designer.getId()),
                candidates.stream().map(candidate -> candidate.getDesignerId()).toList());
        assertEquals(2, candidates.get(0).getAvailableSlots());
        assertEquals(0, candidates.get(1).getAvailableSlots());
    }

    @Test
    void countGroupedByStatusDelegatesToRepository() {
        List<Object[]> expected = List.<Object[]>of(new Object[] { DesignRequestStatus.PENDING, 2L });
        when(designRequestRepository.countGroupedByStatus()).thenReturn(expected);

        assertSame(expected, service.countGroupedByStatus());
    }

    @Test
    void aggregateDailyCreatedDelegatesRangeToRepository() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-02T00:00:00Z");
        List<Object[]> expected = List.<Object[]>of(new Object[] { "2026-08-01", 3L });
        when(designRequestRepository.aggregateDailyCreated(start, end)).thenReturn(expected);

        assertSame(expected, service.aggregateDailyCreated(start, end));
    }

    @Test
    void countDesignRequestsDelegatesToRepository() {
        when(designRequestRepository.count()).thenReturn(12L);

        assertEquals(12L, service.countDesignRequests());
    }

    @Test
    void cancelRequestRejectsNonPendingRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        stubCompanyRequest(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.cancelRequest(exhibitor, requestId));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void requestCancellationForPendingRequestCancelsImmediately() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = pendingRequest(requestId);
        stubCompanyRequest(request);
        stubSaveAndResponse(request);

        service.requestCancellation(exhibitor, requestId, "not required");

        assertEquals(DesignRequestStatus.CANCELED, request.getStatus());
        assertEquals(BoothStatus.DRAFT, booth.getStatus());
    }

    @Test
    void requestCancellationRejectsTerminalRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.APPROVED);
        stubCompanyRequest(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.requestCancellation(exhibitor, requestId, "Changed plan"));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
    }

    @Test
    void requestCancellationRequiresReasonAfterAssignment() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        stubCompanyRequest(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.requestCancellation(exhibitor, requestId, "  "));

        assertSame(ErrorCode.DESIGN_CANCELLATION_REASON_REQUIRED, exception.getErrorCode());
        verify(designRequestRepository, never()).save(any());
    }

    @Test
    void decideCancellationRejectsWhenNoDecisionIsPending() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.decideCancellation(requestId, false, "No"));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
    }

    @Test
    void decideCancellationRejectionKeepsMainWorkflow() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setCancellationStatus(DesignRequestCancellationStatus.REQUESTED);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        stubSaveAndResponse(request);

        service.decideCancellation(requestId, false, "  Continue work  ");

        assertEquals(DesignRequestStatus.ASSIGNED, request.getStatus());
        assertEquals(DesignRequestCancellationStatus.REJECTED, request.getCancellationStatus());
        assertEquals("Continue work", request.getCancellationResolutionNote());
    }

    @Test
    void assignRequestAssignsAvailableDesignerAndCreatesBaseline() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = pendingRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityByIdForUpdate(designer.getId())).thenReturn(designer);
        stubSaveAndResponse(request);

        service.assignRequest(requestId, new AssignDesignRequest(designer.getId()));

        assertSame(designer, request.getAssignedDesigner());
        assertEquals(DesignRequestStatus.ASSIGNED, request.getStatus());
        assertEquals(BoothStatus.DESIGNING, booth.getStatus());
        verify(baselineService).createWorkingBaseline(request);
    }

    @Test
    void assignRequestRejectsNonPendingRequestBeforeLoadingDesigner() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.assignRequest(requestId, new AssignDesignRequest(designer.getId())));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
        verify(userService, never()).getUserEntityByIdForUpdate(any());
    }

    @Test
    void saveWorkingDraftAddsVersionZeroWhenNoneExists() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        stubSaveAndResponse(request);

        service.saveWorkingDraft(designer, requestId, new SubmitDesignDraftRequest("Empty", List.of()));

        assertEquals(1, request.getDrafts().size());
        assertEquals(0, request.getDrafts().get(0).getVersionNumber());
        verify(designDraftAssetService).cleanupUnreferencedAssets(request);
    }

    @Test
    void submitWorkingDraftRejectsMissingWorkingDraft() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.submitWorkingDraft(designer, requestId));

        assertSame(ErrorCode.DESIGN_DRAFT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void submitWorkingDraftRejectsDifferentDesigner() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        User otherDesigner = User.builder().id(UUID.randomUUID()).role(Role.DESIGNER).build();
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.submitWorkingDraft(otherDesigner, requestId));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void submitWorkingDraftRejectsPendingCancellation() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setCancellationStatus(DesignRequestCancellationStatus.REQUESTED);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.submitWorkingDraft(designer, requestId));

        assertSame(ErrorCode.DESIGN_CANCELLATION_PENDING, exception.getErrorCode());
    }

    @Test
    void rejectDraftRejectsNonSubmittedRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        stubCompanyRequest(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.rejectDraft(exhibitor, requestId, new RejectDesignDraftRequest("Revise")));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
    }

    @Test
    void rejectDraftStoresReasonOnLatestSubmittedVersion() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft older = DesignDraft.builder().designRequest(request).versionNumber(1).build();
        DesignDraft latest = DesignDraft.builder().designRequest(request).versionNumber(2).build();
        request.getDrafts().addAll(List.of(older, latest));
        stubCompanyRequest(request);
        stubSaveAndResponse(request);

        service.rejectDraft(exhibitor, requestId, new RejectDesignDraftRequest("  Move entrance  "));

        assertEquals("Move entrance", latest.getRejectionReason());
        assertEquals(DesignRequestStatus.REVISION_REQUESTED, request.getStatus());
        verify(draftCloneService).cloneLatestSubmittedToWorking(request);
    }

    @Test
    void approveDraftRejectsNonSubmittedRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        stubCompanyRequest(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.approveDraft(exhibitor, requestId));

        assertSame(ErrorCode.INVALID_DESIGN_REQUEST_STATUS, exception.getErrorCode());
    }

    @Test
    void approveDraftRejectsMissingSubmittedDraft() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        stubCompanyRequest(request);
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.approveDraft(exhibitor, requestId));

        assertSame(ErrorCode.DESIGN_DRAFT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void approveDraftRejectsVersionZeroDraft() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.DRAFT_SUBMITTED);
        DesignDraft working = DesignDraft.builder().designRequest(request).versionNumber(0).build();
        stubCompanyRequest(request);
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(working));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.approveDraft(exhibitor, requestId));

        assertSame(ErrorCode.DESIGN_DRAFT_VERSION_INVALID, exception.getErrorCode());
    }

    @Test
    void cleanupTerminalAssetsCleansApprovedRequest() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.APPROVED);
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftAssetService.cleanupAfterApproval(request)).thenReturn(2);

        assertEquals(2, service.cleanupTerminalAssets(requestId));
        verify(designDraftRepository, never()).flush();
    }

    @Test
    void cleanupTerminalAssetsClearsCanceledDraftsBeforeCleanup() {
        UUID requestId = UUID.randomUUID();
        DesignRequest request = assignedRequest(requestId);
        request.setStatus(DesignRequestStatus.CANCELED);
        request.getDrafts().add(DesignDraft.builder().designRequest(request).versionNumber(0).build());
        when(designRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(designDraftAssetService.cleanupAfterApproval(request)).thenReturn(3);

        assertEquals(3, service.cleanupTerminalAssets(requestId));
        assertTrue(request.getDrafts().isEmpty());
        verify(designDraftRepository).flush();
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
        booth.setStatus(BoothStatus.DESIGNING);
        return request;
    }

    private void stubCompanyRequest(DesignRequest request) {
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(designRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
    }

    private DesignRequestResponseDTO stubSaveAndResponse(DesignRequest request) {
        DesignRequestResponseDTO response = new DesignRequestResponseDTO();
        when(designRequestRepository.save(request)).thenReturn(request);
        when(designRequestMapper.toResponse(request)).thenReturn(response);
        return response;
    }
}
