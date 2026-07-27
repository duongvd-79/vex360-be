package com.example.vex360.features.partnership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.mapper.PartnershipRequestMapper;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import static org.mockito.ArgumentMatchers.eq;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.enums.PartnershipAccountAction;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

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
                partnershipRequestRepository,
                userService,
                companyService,
                mailService,
                Mappers.getMapper(PartnershipRequestMapper.class));

        ReflectionTestUtils.setField(partnershipRequestService, "backendBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(partnershipRequestService, "partnershipFrontendUrl", "http://localhost:3000/register");

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("User Name")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void submitGuestRequestCreatesAwaitingVerificationRequest() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);

        when(userService.existsByEmail("guest@example.com")).thenReturn(false);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> {
            PartnershipRequest savedRequest = invocation.getArgument(0);
            savedRequest.setId(UUID.randomUUID());
            return savedRequest;
        });

        PartnershipRequestResponseDTO response = partnershipRequestService.submitGuestRequest(request);

        ArgumentCaptor<PartnershipRequest> captor = ArgumentCaptor.forClass(PartnershipRequest.class);
        verify(partnershipRequestRepository).save(captor.capture());
        PartnershipRequest savedRequest = captor.getValue();

        assertNull(savedRequest.getSubmittedByUser());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT, savedRequest.getAccountAction());
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION, savedRequest.getStatus());
        assertEquals(Role.EXHIBITOR, savedRequest.getRequestedRole());
        assertEquals("guest@example.com", response.getRequesterEmail());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals("AWAITING_VERIFICATION", response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void submitGuestRequestThrowsWhenEmailAlreadyExists() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.ORGANIZER);

        when(userService.existsByEmail("user@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void submitGuestRequestThrowsWhenEmailHasPendingRequest() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);

        when(userService.existsByEmail("guest@example.com")).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus(
                "guest@example.com",
                PartnershipRequestStatus.PENDING)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void submitAuthenticatedRequestCreatesPendingRequestWhenEmailMatchesUserEmail() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.EXHIBITOR);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user,
                request);

        assertEquals(user.getId(), response.getSubmittedByUserId());
        assertEquals("user@example.com", response.getRequesterEmail());
        assertEquals(PartnershipAccountAction.UPGRADE_EXISTING_USER.name(), response.getAccountAction());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void submitAuthenticatedRequestCreatesAwaitingVerificationRequestWhenEmailDiffersFromUserEmail() {
        SubmitPartnershipRequest request = validRequest("company@example.com", Role.ORGANIZER);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.existsByEmail("company@example.com")).thenReturn(false);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> {
            PartnershipRequest savedRequest = invocation.getArgument(0);
            savedRequest.setId(UUID.randomUUID());
            return savedRequest;
        });

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, request);

        ArgumentCaptor<PartnershipRequest> captor = ArgumentCaptor.forClass(PartnershipRequest.class);
        verify(partnershipRequestRepository).save(captor.capture());
        PartnershipRequest savedRequest = captor.getValue();

        assertNull(response.getSubmittedByUserId());
        assertEquals("company@example.com", response.getRequesterEmail());
        assertNull(savedRequest.getSubmittedByUser());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT, savedRequest.getAccountAction());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION, savedRequest.getStatus());
        assertEquals("AWAITING_VERIFICATION", response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenUserHasPendingRequest() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.ORGANIZER);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(
                user.getId(),
                PartnershipRequestStatus.PENDING)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(user, request));

        assertSame(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenRequesterEmailHasPendingRequest() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.ORGANIZER);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(
                user.getId(),
                PartnershipRequestStatus.PENDING)).thenReturn(false);
        when(partnershipRequestRepository.existsByRequesterEmailAndStatus(
                "user@example.com",
                PartnershipRequestStatus.PENDING)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(user, request));

        assertSame(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void approveGuestRequestCreatesUserCompanyAndSendsCredentials() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.existsByEmail("requester@example.com")).thenReturn(false);
        when(userService.createUser(any(CreateUserRequest.class), any(UserStatus.class)))
                .thenAnswer(invocation -> {
                    CreateUserRequest req = invocation.getArgument(0);
                    UserStatus status = invocation.getArgument(1);
                    return User.builder()
                            .id(UUID.randomUUID())
                            .email(req.getEmail())
                            .password("encodedPassword")
                            .fullName(req.getFullName())
                            .phoneNumber(req.getPhoneNumber())
                            .role(req.getRole())
                            .status(status)
                            .build();
                });
        when(companyService.createCompany(any(User.class), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            User owner = invocation.getArgument(0);
            String name = invocation.getArgument(1);
            String email = invocation.getArgument(2);
            String phone = invocation.getArgument(3);
            return Company.builder()
                    .id(UUID.randomUUID())
                    .ownerUser(owner)
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .status(CompanyStatus.INCOMPLETE_PROFILE)
                    .build();
        });
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        ArgumentCaptor<CreateUserRequest> userReqCaptor = ArgumentCaptor.forClass(CreateUserRequest.class);
        ArgumentCaptor<User> ownerCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).createUser(userReqCaptor.capture(), eq(UserStatus.ACTIVE));
        verify(companyService).createCompany(ownerCaptor.capture(), nameCaptor.capture(),
                emailCaptor.capture(), phoneCaptor.capture());

        CreateUserRequest savedUserReq = userReqCaptor.getValue();
        String savedCompanyName = nameCaptor.getValue();
        String savedPhone = phoneCaptor.getValue();

        assertEquals("requester@example.com", savedUserReq.getEmail());
        assertEquals(Role.EXHIBITOR, savedUserReq.getRole());
        assertEquals("Vex360 Partner", savedCompanyName);
        assertEquals("0912345678", savedPhone);
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT.name(), response.getAccountAction());
        assertEquals("APPROVED", response.getStatus());
        assertNotNull(request.getReviewedAt());
        verify(mailService).sendNewUserCredentialsEmail(anyString(), anyString(), anyString());
    }

    @Test
    void approveAuthenticatedRequestUpdatesRoleAndCreatesCompanyWhenMissing() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(false);
        when(companyService.createCompany(any(User.class), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Company.builder().build());
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        assertEquals(Role.ORGANIZER, user.getRole());
        assertEquals(PartnershipAccountAction.UPGRADE_EXISTING_USER.name(), response.getAccountAction());
        assertEquals("APPROVED", response.getStatus());
        verify(companyService).createCompany(any(User.class), anyString(), anyString(), anyString());
        verify(mailService).sendPartnershipApprovedEmail(
                "user@example.com",
                "User Name",
                Role.ORGANIZER,
                "Vex360 Partner");
    }

    @Test
    void approveAuthenticatedRequestDoesNotCreateCompanyWhenAlreadyExists() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.ORGANIZER);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(userService.saveUserEntity(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyService.existsByOwnerUserId(user.getId())).thenReturn(true);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        partnershipRequestService.approveRequest(requestId);

        verify(companyService, never()).createCompany(any(User.class), anyString(), anyString(), anyString());
    }

    @Test
    void rejectAuthenticatedRequestSetsStatusNoteReviewedAtAndSendsEmailToUserEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.rejectRequest(
                requestId,
                new RejectPartnershipRequest("Not a fit"));

        assertEquals("REJECTED", response.getStatus());
        assertEquals("Not a fit", response.getReviewNote());
        assertNotNull(response.getReviewedAt());
        verify(mailService).sendPartnershipRejectedEmail(
                "user@example.com",
                "User Name",
                "Vex360 Partner",
                "Not a fit");
    }

    @Test
    void rejectGuestRequestSendsEmailToRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        partnershipRequestService.rejectRequest(requestId,
                new RejectPartnershipRequest("Missing company documents"));

        verify(mailService).sendPartnershipRejectedEmail(
                "requester@example.com",
                "Requester Name",
                "Vex360 Partner",
                "Missing company documents");
    }

    @Test
    void approveOrRejectThrowsWhenRequestIsNotPending() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setStatus(PartnershipRequestStatus.APPROVED);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        AppException approveException = assertThrows(AppException.class,
                () -> partnershipRequestService.approveRequest(requestId));
        AppException rejectException = assertThrows(AppException.class,
                () -> partnershipRequestService.rejectRequest(requestId,
                        new RejectPartnershipRequest("No")));

        assertSame(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS, approveException.getErrorCode());
        assertSame(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS, rejectException.getErrorCode());
        verify(userService, never()).createUser(any(CreateUserRequest.class), any(UserStatus.class));
    }

    @Test
    void getRequestsFiltersByStatusAndRole() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.asc("companyName"),
                Sort.Order.desc("contactPerson"),
                Sort.Order.asc("email"),
                Sort.Order.desc("submittedAt")));
        PageRequest mappedPageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.asc("organizationName"),
                Sort.Order.desc("requesterName"),
                Sort.Order.asc("requesterEmail"),
                Sort.Order.desc("createdAt")));
        PartnershipRequest request = pendingRequest(UUID.randomUUID(), user, Role.ORGANIZER);

        when(partnershipRequestRepository.searchRequests(
                "Partner", PartnershipRequestStatus.PENDING, Role.ORGANIZER, mappedPageable))
                .thenReturn(new PageImpl<>(List.of(request), mappedPageable, 1));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                " Partner ",
                PartnershipRequestStatus.PENDING,
                Role.ORGANIZER,
                pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("ORGANIZER", response.getContent().get(0).getRequestedRole());
    }

    @Test
    void getRequests_RequestedRoleNull_DoesNotThrowException() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(partnershipRequestRepository.searchRequests(null, PartnershipRequestStatus.PENDING, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                " ",
                PartnershipRequestStatus.PENDING,
                null,
                pageable);

        assertNotNull(response);
        assertEquals(0, response.getContent().size());
    }

    @Test
    void countPendingRequestsReturnsPendingCount() {
        when(partnershipRequestRepository.countByStatus(PartnershipRequestStatus.PENDING)).thenReturn(5L);

        long count = partnershipRequestService.countPendingRequests();

        assertEquals(5L, count);
        verify(partnershipRequestRepository).countByStatus(PartnershipRequestStatus.PENDING);
    }

    @Test
    void approveGuestRequest_EmailAlreadyRegistered_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userService.existsByEmail("requester@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.approveRequest(requestId));

        assertSame(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED, exception.getErrorCode());
        verify(userService, never()).createUser(any(), any());
    }

    @Test
    void submitGuestRequest_RequestNull_ThrowsException() {
        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(null));

        assertSame(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void submitGuestRequest_AcceptedPolicyNull_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        request.setAcceptedPolicy(null);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void submitGuestRequest_AcceptedPolicyFalse_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        request.setAcceptedPolicy(false);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void submitGuestRequest_RoleNull_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", null);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.INVALID_PARTNERSHIP_ROLE, exception.getErrorCode());
    }

    @Test
    void submitGuestRequest_RoleInvalid_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.VISITOR);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.INVALID_PARTNERSHIP_ROLE, exception.getErrorCode());
    }

    @Test
    void submitAuthenticatedRequest_RequesterEmailNull_CreatesAwaitingVerificationRequest() {
        SubmitPartnershipRequest request = validRequest(null, Role.EXHIBITOR);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> {
            PartnershipRequest savedRequest = invocation.getArgument(0);
            savedRequest.setId(UUID.randomUUID());
            return savedRequest;
        });

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, request);
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION.name(), response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(eq(null), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequest_UserEmailNull_CreatesAwaitingVerificationRequest() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.EXHIBITOR);
        User userWithNullEmail = User.builder().id(UUID.randomUUID()).email(null).build();

        when(userService.getUserEntityById(userWithNullEmail.getId())).thenReturn(userWithNullEmail);
        when(userService.existsByEmail("user@example.com")).thenReturn(false);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> {
            PartnershipRequest savedRequest = invocation.getArgument(0);
            savedRequest.setId(UUID.randomUUID());
            return savedRequest;
        });

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(userWithNullEmail, request);
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION.name(), response.getStatus());
        verify(mailService).sendPartnershipVerificationEmail(eq("user@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void submitAuthenticatedRequest_CurrentUserNull_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.EXHIBITOR);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(null, request));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void submitAuthenticatedRequest_CurrentUserIdNull_ThrowsException() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.EXHIBITOR);
        User badUser = User.builder().id(null).email("user@example.com").build();

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(badUser, request));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void getRequestById_NotFound_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.getRequestById(requestId));

        assertSame(ErrorCode.PARTNERSHIP_REQUEST_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void submitGuestRequest_NormalizeTrimEmpty_ReturnsNullFields() {
        SubmitPartnershipRequest request = new SubmitPartnershipRequest(
                "   ", "guest@example.com", "   ", "   ", Role.EXHIBITOR, "   ", true);

        when(userService.existsByEmail("guest@example.com")).thenReturn(false);
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> {
            PartnershipRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        partnershipRequestService.submitGuestRequest(request);

        ArgumentCaptor<PartnershipRequest> captor = ArgumentCaptor.forClass(PartnershipRequest.class);
        verify(partnershipRequestRepository).save(captor.capture());
        PartnershipRequest saved = captor.getValue();

        assertNull(saved.getRequesterName());
        assertNull(saved.getRequesterPhoneNumber());
        assertNull(saved.getOrganizationName());
        assertNull(saved.getMessage());
    }

    @Test
    void resolveNotificationEmail_SubmittedUserEmailNull_ReturnsRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        User submitted = User.builder().id(UUID.randomUUID()).email(null).build();
        PartnershipRequest request = pendingRequest(requestId, submitted, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(
                eq("requester@example.com"),
                anyString(),
                anyString(),
                anyString());
    }

    @Test
    void resolveNotificationEmail_RequesterEmailNull_ReturnsUserEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail(null);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(
                eq("user@example.com"),
                anyString(),
                anyString(),
                anyString());
    }

    @Test
    void resolveNotificationEmail_DifferentEmails_ReturnsRequesterEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail("different@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(
                eq("different@example.com"),
                anyString(),
                anyString(),
                anyString());
    }

    @Test
    void resolveNotificationName_FullNameNull_ReturnsRequesterName() {
        UUID requestId = UUID.randomUUID();
        User submitted = User.builder().id(UUID.randomUUID()).email("user@example.com").fullName(null).build();
        PartnershipRequest request = pendingRequest(requestId, submitted, Role.EXHIBITOR);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No"));

        verify(mailService).sendPartnershipRejectedEmail(
                anyString(),
                eq("Requester Name"),
                anyString(),
                anyString());
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
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.approveRequest(requestId);

        verify(mailService).sendPartnershipApprovedEmail(
                eq("different@example.com"),
                eq("Requester Name"),
                eq(Role.ORGANIZER),
                anyString());
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
        when(partnershipRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        partnershipRequestService.approveRequest(requestId);

        verify(mailService).sendPartnershipApprovedEmail(
                eq("user@example.com"),
                eq("User Name"),
                eq(Role.ORGANIZER),
                anyString());
    }

    @Test
    void getRequestById_Exists_ReturnsPartnershipRequestResponseDTO() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, null, Role.EXHIBITOR);

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        PartnershipRequestResponseDTO response = partnershipRequestService.getRequestById(requestId);

        assertNotNull(response);
        assertEquals(requestId, response.getId());
        assertEquals("requester@example.com", response.getRequesterEmail());
    }

    private SubmitPartnershipRequest validRequest(String email, Role role) {
        return new SubmitPartnershipRequest(
                "Requester Name",
                email,
                "0912345678",
                "Vex360 Partner",
                role,
                "We want to partner",
                true);
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
                .accountAction(submittedByUser == null
                        ? PartnershipAccountAction.CREATE_NEW_ACCOUNT
                        : PartnershipAccountAction.UPGRADE_EXISTING_USER)
                .message("We want to partner")
                .acceptedPolicy(true)
                .status(PartnershipRequestStatus.PENDING)
                .build();
    }

    @Test
    void verifyRequest_Confirm_Success() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = PartnershipRequest.builder()
                .id(requestId)
                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                .status(PartnershipRequestStatus.AWAITING_VERIFICATION)
                .createdAt(Instant.now())
                .build();

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        String encryptedToken = TokenEncryptionUtils.encrypt(requestId.toString());
        String result = partnershipRequestService.verifyRequest(encryptedToken);

        assertEquals(PartnershipRequestStatus.PENDING, request.getStatus());
        assertEquals(PartnershipAccountAction.CREATE_NEW_ACCOUNT, request.getAccountAction());
        assertTrue(result.contains("partnership_confirmed=true"));
        verify(partnershipRequestRepository).save(request);
    }

    @Test
    void verifyRequest_Expired_RetainsAndReturnsExpired() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = PartnershipRequest.builder()
                .id(requestId)
                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                .status(PartnershipRequestStatus.AWAITING_VERIFICATION)
                .createdAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .build();

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String encryptedToken = TokenEncryptionUtils.encrypt(requestId.toString());
        String result = partnershipRequestService.verifyRequest(encryptedToken);

        assertTrue(result.contains("partnership_error=expired"));
        assertEquals(PartnershipRequestStatus.AWAITING_VERIFICATION, request.getStatus());
        verify(partnershipRequestRepository, never()).delete(request);
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void verifyRequest_AlreadyProcessed_ReturnsAlreadyProcessed() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = PartnershipRequest.builder()
                .id(requestId)
                .accountAction(PartnershipAccountAction.CREATE_NEW_ACCOUNT)
                .status(PartnershipRequestStatus.PENDING)
                .build();

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        String encryptedToken = TokenEncryptionUtils.encrypt(requestId.toString());
        String result = partnershipRequestService.verifyRequest(encryptedToken);

        assertTrue(result.contains("partnership_error=already_processed"));
    }
}
