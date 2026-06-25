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

import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestSummaryResponseDTO;
import com.example.vex360.features.partnership.mapper.PartnershipRequestMapper;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.features.user.repositories.UserRepository;
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
    private UserRepository userRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    private PartnershipRequestService partnershipRequestService;
    private User user;

    @BeforeEach
    void setup() {
        partnershipRequestService = new PartnershipRequestService(
                partnershipRequestRepository,
                userRepository,
                companyRepository,
                passwordEncoder,
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

        when(userRepository.existsByEmail("guest@example.com")).thenReturn(false);
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

        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitGuestRequest(request));

        assertSame(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void submitGuestRequestThrowsWhenEmailHasPendingRequest() {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);

        when(userRepository.existsByEmail("guest@example.com")).thenReturn(false);
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

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.submitAuthenticatedRequest(user, request);

        assertEquals(user.getId(), response.getSubmittedByUserId());
        assertEquals("user@example.com", response.getRequesterEmail());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenRequesterEmailDiffersFromUserEmail() {
        SubmitPartnershipRequest request = validRequest("company@example.com", Role.ORGANIZER);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class,
                () -> partnershipRequestService.submitAuthenticatedRequest(user, request));

        assertSame(ErrorCode.PARTNERSHIP_REQUESTER_EMAIL_MUST_MATCH_AUTHENTICATED_USER, exception.getErrorCode());
        verify(partnershipRequestRepository, never()).save(any(PartnershipRequest.class));
    }

    @Test
    void submitAuthenticatedRequestThrowsWhenUserHasPendingRequest() {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.ORGANIZER);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
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

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
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
        when(userRepository.existsByEmail("requester@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(UUID.randomUUID());
            return savedUser;
        });
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(userRepository).save(userCaptor.capture());
        verify(companyRepository).save(companyCaptor.capture());

        User savedUser = userCaptor.getValue();
        Company savedCompany = companyCaptor.getValue();

        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals(Role.EXHIBITOR, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertEquals(savedUser, savedCompany.getOwnerUser());
        assertEquals("Vex360 Partner", savedCompany.getName());
        assertEquals(CompanyStatus.INCOMPLETE_PROFILE, savedCompany.getStatus());
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
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyRepository.existsByOwnerUserId(user.getId())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartnershipRequestResponseDTO response = partnershipRequestService.approveRequest(requestId);

        assertEquals(Role.ORGANIZER, user.getRole());
        assertEquals("APPROVED", response.getStatus());
        verify(companyRepository).save(any(Company.class));
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
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyRepository.existsByOwnerUserId(user.getId())).thenReturn(true);
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        partnershipRequestService.approveRequest(requestId);

        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void rejectAuthenticatedRequestSetsStatusNoteReviewedAtAndSendsEmailToUserEmail() {
        UUID requestId = UUID.randomUUID();
        PartnershipRequest request = pendingRequest(requestId, user, Role.EXHIBITOR);
        request.setRequesterEmail("user@example.com");

        when(partnershipRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(partnershipRequestRepository.save(any(PartnershipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("Missing company documents"));

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
                () -> partnershipRequestService.rejectRequest(requestId, new RejectPartnershipRequest("No")));

        assertSame(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS, approveException.getErrorCode());
        assertSame(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS, rejectException.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getRequestsFiltersByStatusAndRole() {
        PageRequest pageable = PageRequest.of(0, 10);
        PartnershipRequest request = pendingRequest(UUID.randomUUID(), user, Role.ORGANIZER);

        when(partnershipRequestRepository.searchRequests(PartnershipRequestStatus.PENDING, Role.ORGANIZER, pageable))
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
