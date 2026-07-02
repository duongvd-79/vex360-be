package com.example.vex360.features.partnership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestSummaryResponseDTO;
import com.example.vex360.features.partnership.mapper.PartnershipRequestMapper;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.UserRequestDTO;
import static org.mockito.ArgumentMatchers.eq;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.PartnershipRequest;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.CompanyStatus;
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

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("User Name")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void submitGuestRequestCreatesPendingRequest() {
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
        assertEquals(PartnershipRequestStatus.PENDING, savedRequest.getStatus());
        assertEquals(Role.EXHIBITOR, savedRequest.getRequestedRole());
        assertEquals("guest@example.com", response.getRequesterEmail());
        assertEquals("PENDING", response.getStatus());
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
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenRequesterEmailDiffersFromUserEmail() {
        SubmitPartnershipRequest request = validRequest("company@example.com", Role.ORGANIZER);

        when(userService.getUserEntityById(user.getId())).thenReturn(user);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(user, request));

        assertSame(ErrorCode.PARTNERSHIP_REQUESTER_EMAIL_MUST_MATCH_AUTHENTICATED_USER,
                exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
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
        when(userService.createUser(any(UserRequestDTO.class), any(UserStatus.class)))
                .thenAnswer(invocation -> {
                    UserRequestDTO req = invocation.getArgument(0);
                    UserStatus status = invocation.getArgument(1);
                    return User.builder()
                            .id(UUID.randomUUID())
                            .email(req.getEmail())
                            .password("encodedPassword")
                            .fullName(req.getFullName())
                            .phoneNumber(req.getPhoneNumber())
                            .role(Role.valueOf(req.getRole()))
                            .status(status)
                            .build();
                });
        when(companyService.createCompany(any(User.class), anyString(), anyString())).thenAnswer(invocation -> {
            User owner = invocation.getArgument(0);
            String name = invocation.getArgument(1);
            String email = invocation.getArgument(2);
            return Company.builder()
                    .id(UUID.randomUUID())
                    .ownerUser(owner)
                    .name(name)
                    .email(email)
                    .status(CompanyStatus.INCOMPLETE_PROFILE)
                    .build();
        });
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        ArgumentCaptor<UserRequestDTO> userReqCaptor = ArgumentCaptor.forClass(UserRequestDTO.class);
        ArgumentCaptor<User> ownerCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).createUser(userReqCaptor.capture(), eq(UserStatus.ACTIVE));
        verify(companyService).createCompany(ownerCaptor.capture(), nameCaptor.capture(),
                emailCaptor.capture());

        UserRequestDTO savedUserReq = userReqCaptor.getValue();
        String savedCompanyName = nameCaptor.getValue();

        assertEquals("requester@example.com", savedUserReq.getEmail());
        assertEquals(Role.EXHIBITOR.name(), savedUserReq.getRole());
        assertEquals("Vex360 Partner", savedCompanyName);
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
        when(companyService.createCompany(any(User.class), anyString(), anyString()))
                .thenAnswer(invocation -> Company.builder().build());
        when(partnershipRequestRepository.save(any(PartnershipRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        assertEquals(Role.ORGANIZER, user.getRole());
        assertEquals("APPROVED", response.getStatus());
        verify(companyService).createCompany(any(User.class), anyString(), anyString());
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

        verify(companyService, never()).createCompany(any(User.class), anyString(), anyString());
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
        verify(userService, never()).createUser(any(UserRequestDTO.class), any(UserStatus.class));
    }

    @Test
    void getRequestsFiltersByStatusAndRole() {
        PageRequest pageable = PageRequest.of(0, 10);
        PartnershipRequest request = pendingRequest(UUID.randomUUID(), user, Role.ORGANIZER);

        when(partnershipRequestRepository.searchRequests(PartnershipRequestStatus.PENDING, Role.ORGANIZER,
                pageable))
                .thenReturn(new PageImpl<>(List.of(request), pageable, 1));

        PageResponse<PartnershipRequestResponseDTO> response = partnershipRequestService.getRequests(
                PartnershipRequestStatus.PENDING,
                Role.ORGANIZER,
                pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("ORGANIZER", response.getContent().get(0).getRequestedRole());
    }

    @Test
    void getRequestSummaryCountsRequestsByStatus() {
        when(partnershipRequestRepository.countByStatus(PartnershipRequestStatus.PENDING)).thenReturn(5L);
        when(partnershipRequestRepository.countByStatus(PartnershipRequestStatus.APPROVED)).thenReturn(4L);
        when(partnershipRequestRepository.countByStatus(PartnershipRequestStatus.REJECTED)).thenReturn(3L);

        PartnershipRequestSummaryResponseDTO response = partnershipRequestService.getRequestSummary();

        assertEquals(5L, response.getPendingRequests());
        assertEquals(4L, response.getApprovedRequests());
        assertEquals(3L, response.getRejectedRequests());
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
                .message("We want to partner")
                .acceptedPolicy(true)
                .status(PartnershipRequestStatus.PENDING)
                .build();
    }
}
