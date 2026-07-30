package com.example.vex360.features.partnership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.features.partnership.mapper.PartnershipRequestMapper;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.PartnershipAccountAction;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

@ExtendWith(MockitoExtension.class)
class PartnershipRequestServiceUnitTest {
    @Mock
    private PartnershipRequestRepository partnershipRequestRepository;
    @Mock
    private UserService userService;
    @Mock
    private CompanyService companyService;
    @Mock
    private MailService mailService;

    private PartnershipRequestService partnershipRequestService;
    private User user;

    @BeforeEach
    void setup() {
        partnershipRequestService = new PartnershipRequestService(
                partnershipRequestRepository, userService, companyService, mailService,
                Mappers.getMapper(PartnershipRequestMapper.class));

        ReflectionTestUtils.setField(partnershipRequestService, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(partnershipRequestService, "partnershipFrontendUrl", "http://localhost:3000/register");
        lenient().when(partnershipRequestRepository.save(any())).thenAnswer(inv -> {
            PartnershipRequest r = inv.getArgument(0);
            if (r != null && r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        user = User.builder().id(UUID.randomUUID()).email("user@example.com").fullName("User Name")
                .role(Role.VISITOR).status(UserStatus.ACTIVE).build();
    }

    @Test
    void submitGuestRequestCreatesAwaitingVerificationRequest() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        when(userService.existsByEmail("guest@example.com")).thenReturn(false);

        PartnershipRequestResponseDTO response = partnershipRequestService.submitGuestRequest(request);

        assertEquals("guest@example.com", response.getRequesterEmail());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals("AWAITING_VERIFICATION", response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void submitGuestRequestThrowsWhenEmailAlreadyExists() {
        when(userService.existsByEmail("user@example.com")).thenReturn(true);
        assertEquals(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(validRequest("user@example.com", Role.ORGANIZER))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitGuestRequestThrowsWhenEmailHasPendingRequest() {
        when(userService.existsByEmail("guest@example.com")).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("guest@example.com", PartnershipRequestStatus.PENDING)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(validRequest("guest@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequestCreatesPendingRequestWhenEmailMatchesUserEmail() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, validRequest("user@example.com", Role.EXHIBITOR));

        assertEquals(user.getId(), response.getSubmittedByUserId());
        assertEquals("user@example.com", response.getRequesterEmail());
        assertEquals(PartnershipAccountAction.UPGRADE_EXISTING_USER.name(), response.getAccountAction());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void submitAuthenticatedRequestCreatesAwaitingVerificationRequestWhenEmailDiffersFromUserEmail() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.existsByEmail("company@example.com")).thenReturn(false);

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, validRequest("company@example.com", Role.ORGANIZER));

        assertNull(response.getSubmittedByUserId());
        assertEquals("company@example.com", response.getRequesterEmail());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals("AWAITING_VERIFICATION", response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenUserHasPendingRequest() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.PENDING)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("user@example.com", Role.ORGANIZER))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenRequesterEmailHasPendingRequest() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("user@example.com", PartnershipRequestStatus.PENDING)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("user@example.com", Role.ORGANIZER))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void approveGuestRequestCreatesUserCompanyAndSendsCredentials() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.existsByEmail("requester@example.com")).thenReturn(false);
        when(userService.createUser(any(), any())).thenAnswer(inv -> User.builder().id(UUID.randomUUID())
                .email(inv.getArgument(0, CreateUserRequest.class).getEmail())
                .fullName(inv.getArgument(0, CreateUserRequest.class).getFullName())
                .role(inv.getArgument(0, CreateUserRequest.class).getRole())
                .status(inv.getArgument(1, UserStatus.class)).build());
        when(companyService.createCompany(any(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> Company.builder().id(UUID.randomUUID()).name(inv.getArgument(1)).phone(inv.getArgument(3)).build());

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals("APPROVED", response.getStatus());
        assertNotNull(request.getReviewedAt());
        verify(userService).createUser(any(CreateUserRequest.class), eq(UserStatus.ACTIVE));
        verify(companyService).createCompany(any(User.class), eq("Vex360 Partner"), eq("requester@example.com"), eq("0912345678"));
        verify(mailService).sendNewUserCredentialsEmail(anyString(), anyString(), anyString());
    }

    @Test
    void approveAuthenticatedRequestUpdatesRoleAndCreatesCompanyWhenMissing() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(false);
        when(companyService.createCompany(any(), anyString(), anyString(), anyString())).thenReturn(Company.builder().build());

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        assertEquals(Role.ORGANIZER, user.getRole());
        assertEquals(PartnershipAccountAction.UPGRADE_EXISTING_USER.name(), response.getAccountAction());
        assertEquals("APPROVED", response.getStatus());
        verify(companyService).createCompany(any(User.class), anyString(), anyString(), anyString());
        verify(mailService).sendPartnershipApprovedEmail("user@example.com", "User Name", Role.ORGANIZER, "Vex360 Partner");
    }

    @Test
    void approveAuthenticatedRequestDoesNotCreateCompanyWhenAlreadyExists() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(true);

        partnershipRequestService.approveRequest(requestId);

        verify(companyService, never()).createCompany(any(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectAuthenticatedRequestSetsStatusNoteReviewedAtAndSendsEmailToUserEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        PartnershipRequestResponseDTO response = partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("Not a fit"));

        assertEquals("REJECTED", response.getStatus());
        assertEquals("Not a fit", response.getReviewNote());
        assertNotNull(response.getReviewedAt());
        verify(mailService).sendPartnershipRejectedEmail("user@example.com", "User Name", "Vex360 Partner", "Not a fit");
    }

    @Test
    void rejectGuestRequestSendsEmailToRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("Missing company documents"));

        verify(mailService).sendPartnershipRejectedEmail("requester@example.com", "Requester Name", "Vex360 Partner", "Missing company documents");
    }

    @Test
    void approveOrRejectThrowsWhenRequestIsNotPending() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setStatus(PartnershipRequestStatus.APPROVED);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertEquals(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS,
                assertThrows(AppException.class, () -> partnershipRequestService.approveRequest(requestId)).getErrorCode());
        assertEquals(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS,
                assertThrows(AppException.class, () -> partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"))).getErrorCode());
        verify(userService, never()).createUser(any(), any());
    }

    @Test
    void getRequestsFiltersByStatusAndRole() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.asc("companyName"), Sort.Order.desc("contactPerson"),
                Sort.Order.asc("email"), Sort.Order.desc("submittedAt")));
        PageRequest mappedPageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.asc("organizationName"), Sort.Order.desc("requesterName"),
                Sort.Order.asc("requesterEmail"), Sort.Order.desc("createdAt")));
        PartnershipRequest request = pendingRequest(UUID.randomUUID(), user, Role.ORGANIZER);

        when(partnershipRequestRepository.searchRequests("Partner", PartnershipRequestStatus.PENDING, Role.ORGANIZER, null, null, mappedPageable))
                .thenReturn(new PageImpl<>(List.of(request), mappedPageable, 1));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                " Partner ", PartnershipRequestStatus.PENDING, Role.ORGANIZER, null, null, pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("ORGANIZER", response.getContent().get(0).getRequestedRole());
    }

    @Test
    void getRequests_RequestedRoleNull_DoesNotThrowException() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(partnershipRequestRepository.searchRequests(null, PartnershipRequestStatus.PENDING, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                " ", PartnershipRequestStatus.PENDING, null, null, null, pageable);

        assertNotNull(response);
        assertEquals(0, response.getContent().size());
    }

    @Test
    void countPendingRequestsReturnsPendingCount() {
        when(partnershipRequestRepository.countByStatus(PartnershipRequestStatus.PENDING)).thenReturn(5L);

        assertEquals(5L, partnershipRequestService.countPendingRequests());
        verify(partnershipRequestRepository).countByStatus(PartnershipRequestStatus.PENDING);
    }

    @Test
    void approveGuestRequest_EmailAlreadyRegistered_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest(requestId, null, Role.EXHIBITOR)));
        when(userService.existsByEmail("requester@example.com")).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED,
                assertThrows(AppException.class, () -> partnershipRequestService.approveRequest(requestId)).getErrorCode());
        verify(userService, never()).createUser(any(), any());
    }

    @Test
    void submitGuestRequest_RequestNull_ThrowsException() {
        assertEquals(ErrorCode.VALIDATION_FAILED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(null)).getErrorCode());
    }

    @Test
    void submitGuestRequest_AcceptedPolicyNull_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        request.setAcceptedPolicy(null);
        assertEquals(ErrorCode.VALIDATION_FAILED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(request)).getErrorCode());
    }

    @Test
    void submitGuestRequest_AcceptedPolicyFalse_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        request.setAcceptedPolicy(false);
        assertEquals(ErrorCode.VALIDATION_FAILED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(request)).getErrorCode());
    }

    @Test
    void submitGuestRequest_RoleNull_ThrowsException() {
        assertEquals(ErrorCode.INVALID_PARTNERSHIP_ROLE,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(validRequest("guest@example.com", null))).getErrorCode());
    }

    @Test
    void submitGuestRequest_RoleInvalid_ThrowsException() {
        assertEquals(ErrorCode.INVALID_PARTNERSHIP_ROLE,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(validRequest("guest@example.com", Role.VISITOR))).getErrorCode());
    }

    @Test
    void submitAuthenticatedRequest_RequesterEmailNull_CreatesAwaitingVerificationRequest() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, validRequest(null, Role.EXHIBITOR));
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION.name(), response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(eq(null), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequest_UserEmailNull_CreatesAwaitingVerificationRequest() {
        User userWithNullEmail = User.builder().id(UUID.randomUUID()).email(null).build();
        when(userService.getUserEntityById(userWithNullEmail.getId())).thenReturn(userWithNullEmail);
        when(userService.existsByEmail("user@example.com")).thenReturn(false);

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(userWithNullEmail, validRequest("user@example.com", Role.EXHIBITOR));
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION.name(), response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(eq("user@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequest_CurrentUserNull_ThrowsException() {
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(null, validRequest("user@example.com", Role.EXHIBITOR))).getErrorCode());
    }

    @Test
    void submitAuthenticatedRequest_CurrentUserIdNull_ThrowsException() {
        User badUser = User.builder().id(null).email("user@example.com").build();
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(badUser, validRequest("user@example.com", Role.EXHIBITOR))).getErrorCode());
    }

    @Test
    void getRequestById_NotFound_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_NOT_FOUND,
                assertThrows(AppException.class, () -> partnershipRequestService.getRequestById(requestId)).getErrorCode());
    }

    @Test
    void submitGuestRequest_NormalizeTrimEmpty_ReturnsNullFields() {
        SubmitPartnershipRequest request = new SubmitPartnershipRequest("   ", "guest@example.com", "   ", "   ", Role.EXHIBITOR, "   ", true);
        when(userService.existsByEmail("guest@example.com")).thenReturn(false);

        PartnershipRequest[] saved = new PartnershipRequest[1];
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            saved[0].setId(UUID.randomUUID());
            return saved[0];
        });

        partnershipRequestService.submitGuestRequest(request);

        assertNull(saved[0].getRequesterName());
        assertNull(saved[0].getRequesterPhoneNumber());
        assertNull(saved[0].getOrganizationName());
        assertNull(saved[0].getMessage());
    }

    @Test
    void resolveNotificationEmail_SubmittedUserEmailNull_ReturnsRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        User submitted = User.builder().id(UUID.randomUUID()).email(null).build();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest(requestId, submitted, Role.EXHIBITOR)));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(eq("requester@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void resolveNotificationEmail_RequesterEmailNull_ReturnsUserEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail(null);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(eq("user@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void resolveNotificationEmail_DifferentEmails_ReturnsRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail("different@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(eq("different@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void resolveNotificationName_FullNameNull_ReturnsRequesterName() {
        UUID requestId = UUID.randomUUID();
        User submitted = User.builder().id(UUID.randomUUID()).email("user@example.com").fullName(null).build();
        PartnershipRequest request = pendingRequest(requestId, submitted, Role.EXHIBITOR);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(anyString(), eq("Requester Name"), anyString(), anyString());
    }

    @Test
    void approveRequest_AuthenticatedDifferentEmail_ReturnsRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail("different@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(true);

        partnershipRequestService.approveRequest(requestId);

        verify(mailService).sendPartnershipApprovedEmail(eq("different@example.com"), eq("Requester Name"), eq(Role.ORGANIZER), anyString());
    }

    @Test
    void approveRequest_AuthenticatedRequesterEmailNull_ReturnsUserEmailAndName() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail(null);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(true);

        partnershipRequestService.approveRequest(requestId);

        verify(mailService).sendPartnershipApprovedEmail(eq("user@example.com"), eq("User Name"), eq(Role.ORGANIZER), anyString());
    }

    @Test
    void getRequestById_Exists_ReturnsPartnershipRequestResponseDTO() {
        UUID requestId = UUID.randomUUID();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest(requestId, null, Role.EXHIBITOR)));

        PartnershipRequestResponseDTO response = partnershipRequestService.getRequestById(requestId);

        assertNotNull(response);
        assertEquals(requestId, response.getId());
        assertEquals("requester@example.com", response.getRequesterEmail());
    }

    @Test
    void verifyRequest_Confirm_Success() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = requestWithStatus(requestId, PartnershipRequestStatus.AWAITING_VERIFICATION, Instant.now());

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String encryptedToken = TokenEncryptionUtils.encrypt(requestId.toString());
        String result = partnershipRequestService.verifyRequest(encryptedToken);

        assertEquals(PartnershipRequestStatus.PENDING, request.getStatus());
        assertTrue(result.contains("partnership_confirmed=true"));
        verify(partnershipRequestRepository).save(request);
    }

    @Test
    void verifyRequest_Expired_RetainsAndReturnsExpired() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = requestWithStatus(requestId, PartnershipRequestStatus.AWAITING_VERIFICATION, Instant.now().minus(25, ChronoUnit.HOURS));

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String result = partnershipRequestService.verifyRequest(TokenEncryptionUtils.encrypt(requestId.toString()));

        assertTrue(result.contains("partnership_error=expired"));
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION, request.getStatus());
        verify(partnershipRequestRepository, never()).delete(request);
    }

    @Test
    void verifyRequest_AlreadyProcessed_ReturnsAlreadyProcessed() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = requestWithStatus(requestId, PartnershipRequestStatus.PENDING, Instant.now());

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String result = partnershipRequestService.verifyRequest(TokenEncryptionUtils.encrypt(requestId.toString()));

        assertTrue(result.contains("partnership_error=already_processed"));
    }

    @Test
    void submitGuestRequest_EmailHasAwaitingVerificationRequest_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        when(userService.existsByEmail("guest@example.com")).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("guest@example.com", PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("guest@example.com", PartnershipRequestStatus.AWAITING_VERIFICATION)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION,
                assertThrows(AppException.class, () -> partnershipRequestService.submitGuestRequest(request)).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequest_SameEmail_UserHasAwaitingVerificationRequest_ThrowsException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("user@example.com", PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.AWAITING_VERIFICATION)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("user@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequest_SameEmail_EmailHasAwaitingVerificationRequest_ThrowsException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("user@example.com", PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(user.getId(), PartnershipRequestStatus.AWAITING_VERIFICATION)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("user@example.com", PartnershipRequestStatus.AWAITING_VERIFICATION)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("user@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequest_DifferentEmail_EmailAlreadyExists_ThrowsException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.existsByEmail("other@example.com")).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("other@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequest_DifferentEmail_HasPendingRequest_ThrowsException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.existsByEmail("other@example.com")).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("other@example.com", PartnershipRequestStatus.PENDING)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("other@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void submitAuthenticatedRequest_DifferentEmail_HasAwaitingVerificationRequest_ThrowsException() {
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.existsByEmail("other@example.com")).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("other@example.com", PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus("other@example.com", PartnershipRequestStatus.AWAITING_VERIFICATION)).thenReturn(true);

        assertEquals(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION,
                assertThrows(AppException.class, () -> partnershipRequestService.submitAuthenticatedRequest(user, validRequest("other@example.com", Role.EXHIBITOR))).getErrorCode());
        verify(partnershipRequestRepository, never()).save(any());
    }

    @Test
    void getRequests_InvalidRequestedRole_ThrowsException() {
        PageRequest pageable = PageRequest.of(0, 10);
        assertEquals(ErrorCode.INVALID_PARTNERSHIP_ROLE,
                assertThrows(AppException.class, () -> partnershipRequestService.getRequests(null, PartnershipRequestStatus.PENDING, Role.VISITOR, null, null, pageable)).getErrorCode());
    }

    @Test
    void getRequests_WithStartAndEndDates_ConvertsToInstants() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(partnershipRequestRepository.searchRequests(eq(null), eq(PartnershipRequestStatus.PENDING), eq(Role.EXHIBITOR), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                null, PartnershipRequestStatus.PENDING, Role.EXHIBITOR,
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31), pageable);

        assertNotNull(response);
        verify(partnershipRequestRepository).searchRequests(eq(null), eq(PartnershipRequestStatus.PENDING), eq(Role.EXHIBITOR), any(Instant.class), any(Instant.class), eq(pageable));
    }

    @Test
    void verifyRequest_DecryptFails_ReturnsInvalidToken() {
        assertTrue(partnershipRequestService.verifyRequest("invalid_base64_string_####").contains("partnership_error=invalid_token"));
    }

    @Test
    void verifyRequest_InvalidUUID_ReturnsInvalidToken() {
        assertTrue(partnershipRequestService.verifyRequest(TokenEncryptionUtils.encrypt("not-a-valid-uuid-string")).contains("partnership_error=invalid_token"));
    }

    @Test
    void verifyRequest_NotFound_ReturnsNotFound() {
        UUID requestId = UUID.randomUUID();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.empty());
        assertTrue(partnershipRequestService.verifyRequest(TokenEncryptionUtils.encrypt(requestId.toString())).contains("partnership_error=not_found"));
    }

    @Test
    void verifyRequest_NullCreatedAt_ConfirmsSuccessfully() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = requestWithStatus(requestId, PartnershipRequestStatus.AWAITING_VERIFICATION, null);
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String result = partnershipRequestService.verifyRequest(TokenEncryptionUtils.encrypt(requestId.toString()));

        assertEquals(PartnershipRequestStatus.PENDING, request.getStatus());
        assertTrue(result.contains("partnership_confirmed=true"));
        verify(partnershipRequestRepository).save(request);
    }

    private SubmitPartnershipRequest validRequest(String email, Role role) {
        return new SubmitPartnershipRequest("Requester Name", email, "0912345678", "Vex360 Partner", role, "We want to partner", true);
    }

    private PartnershipRequest pendingRequest(UUID id, User submittedByUser, Role requestedRole) {
        return PartnershipRequest.builder()
                .id(id)
                .submittedByUser(submittedByUser)
                .requesterName("Requester Name")
                .requesterEmail("requester@example.com")
                .requesterPhoneNumber("0912345678")
                .organizationName("Vex360 Partner")
                .requestedRole(requestedRole)
                .accountAction(submittedByUser == null ? PartnershipAccountAction.CREATE_NEW_ACCOUNT : PartnershipAccountAction.UPGRADE_EXISTING_USER)
                .message("We want to partner")
                .acceptedPolicy(true)
                .status(PartnershipRequestStatus.PENDING)
                .build();
    }

    private PartnershipRequest requestWithStatus(UUID id, PartnershipRequestStatus status, Instant createdAt) {
        return PartnershipRequest.builder()
                .id(id)
                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}
