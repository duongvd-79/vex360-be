package com.example.vex360.features.partnership.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestSummaryResponseDTO;
import com.example.vex360.features.partnership.mapper.PartnershipRequestMapper;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.partnership.entities.PartnershipRequest;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.PartnershipAccountAction;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.RandomPasswordGenerator;
import com.example.vex360.shared.utils.PageableUtils;
import com.example.vex360.shared.utils.TokenEncryptionUtils;

import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartnershipRequestService {
    private static final Map<String, String> ADMIN_SORT_ALIASES = Map.of(
            "companyName", "organizationName",
            "contactPerson", "requesterName",
            "email", "requesterEmail",
            "submittedAt", "createdAt");

    private final PartnershipRequestRepository partnershipRequestRepository;
    private final UserService userService;
    private final CompanyService companyService;
    private final MailService mailService;
    private final PartnershipRequestMapper partnershipRequestMapper;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Value("${app.partnership.frontend-url}")
    private String partnershipFrontendUrl;

    @Transactional
    public PartnershipRequestResponseDTO submitGuestRequest(SubmitPartnershipRequest request) {
        validateSubmission(request);
        String requesterEmail = normalize(request.getRequesterEmail());

        if (userService.existsByEmail(requesterEmail)) {
            throw new AppException(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED);
        }
        if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                requesterEmail,
                PartnershipRequestStatus.PENDING)) {
            throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING);
        }
        if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                requesterEmail,
                PartnershipRequestStatus.AWAITING_VERIFICATION)) {
            throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION);
        }

        PartnershipRequest partnershipRequest = buildRequest(request, null);
        partnershipRequest.setStatus(PartnershipRequestStatus.AWAITING_VERIFICATION);
        PartnershipRequest savedRequest = partnershipRequestRepository.save(partnershipRequest);
        sendVerificationEmail(savedRequest);
        return partnershipRequestMapper.toResponse(savedRequest);
    }

    @Transactional
    public PartnershipRequestResponseDTO submitAuthenticatedRequest(User currentUser,
            SubmitPartnershipRequest request) {
        validateSubmission(request);
        User submittedByUser = getCurrentUser(currentUser);
        String requesterEmail = normalize(request.getRequesterEmail());

        // Nếu email trùng với tài khoản đang đăng nhập
        if (requesterEmail != null && submittedByUser.getEmail() != null
                && requesterEmail.equalsIgnoreCase(submittedByUser.getEmail())) {
            if (partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(
                    submittedByUser.getId(),
                    PartnershipRequestStatus.PENDING)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING);
            }
            if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                    requesterEmail,
                    PartnershipRequestStatus.PENDING)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING);
            }
            if (partnershipRequestRepository.existsBySubmittedByUserIdAndStatus(
                    submittedByUser.getId(),
                    PartnershipRequestStatus.AWAITING_VERIFICATION)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION);
            }
            if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                    requesterEmail,
                    PartnershipRequestStatus.AWAITING_VERIFICATION)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION);
            }

            PartnershipRequest partnershipRequest = buildRequest(request, submittedByUser);
            partnershipRequest.setStatus(PartnershipRequestStatus.PENDING); // Gửi thẳng trực tiếp lên Admin
            return partnershipRequestMapper.toResponse(partnershipRequestRepository.save(partnershipRequest));
        } else {
            // Nếu email khác với tài khoản đang đăng nhập, hành xử như Guest (yêu cầu xác
            // thực qua email)
            if (userService.existsByEmail(requesterEmail)) {
                throw new AppException(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED);
            }
            if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                    requesterEmail,
                    PartnershipRequestStatus.PENDING)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING);
            }
            if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                    requesterEmail,
                    PartnershipRequestStatus.AWAITING_VERIFICATION)) {
                throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_AWAITING_VERIFICATION);
            }

            PartnershipRequest partnershipRequest = buildRequest(request, null);
            partnershipRequest.setStatus(PartnershipRequestStatus.AWAITING_VERIFICATION);
            PartnershipRequest savedRequest = partnershipRequestRepository.save(partnershipRequest);
            sendVerificationEmail(savedRequest);
            return partnershipRequestMapper.toResponse(savedRequest);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PartnershipRequestResponseDTO> getRequests(
            String keyword,
            PartnershipRequestStatus status,
            Role requestedRole,
            Pageable pageable) {
        if (requestedRole != null) {
            validateRequestedRole(requestedRole);
        }

        Pageable mappedPageable = PageableUtils.remapSort(pageable, ADMIN_SORT_ALIASES);
        Page<PartnershipRequestResponseDTO> requests = partnershipRequestRepository
                .searchRequests(normalize(keyword), status, requestedRole, mappedPageable)
                .map(partnershipRequestMapper::toResponse);
        return PageResponse.from(requests);
    }

    @Transactional(readOnly = true)
    public PartnershipRequestResponseDTO getRequestById(UUID id) {
        return partnershipRequestMapper.toResponse(getRequest(id));
    }

    @Transactional(readOnly = true)
    public PartnershipRequestSummaryResponseDTO getRequestSummary() {
        return new PartnershipRequestSummaryResponseDTO(
                partnershipRequestRepository.countByStatus(PartnershipRequestStatus.AWAITING_VERIFICATION),
                partnershipRequestRepository.countByStatus(PartnershipRequestStatus.PENDING),
                partnershipRequestRepository.countByStatus(PartnershipRequestStatus.APPROVED),
                partnershipRequestRepository.countByStatus(PartnershipRequestStatus.REJECTED));
    }

    @Transactional
    public PartnershipRequestResponseDTO approveRequest(UUID id) {
        PartnershipRequest request = getPendingRequest(id);

        if (request.getSubmittedByUser() == null) {
            approveGuestRequest(request);
        } else {
            approveAuthenticatedRequest(request);
        }
        request.setStatus(PartnershipRequestStatus.APPROVED);
        request.setReviewedAt(Instant.now());
        return partnershipRequestMapper.toResponse(partnershipRequestRepository.save(request));
    }

    @Transactional
    public PartnershipRequestResponseDTO rejectRequest(UUID id, RejectPartnershipRequest rejectRequest) {
        PartnershipRequest request = getPendingRequest(id);
        request.setStatus(PartnershipRequestStatus.REJECTED);
        request.setReviewNote(rejectRequest.getReviewNote());
        request.setReviewedAt(Instant.now());
        PartnershipRequest savedRequest = partnershipRequestRepository.save(request);
        String notificationEmail = resolveNotificationEmail(savedRequest);
        mailService.sendPartnershipRejectedEmail(
                notificationEmail,
                resolveNotificationName(savedRequest, notificationEmail),
                savedRequest.getOrganizationName(),
                savedRequest.getReviewNote());
        return partnershipRequestMapper.toResponse(savedRequest);
    }

    private void approveGuestRequest(PartnershipRequest request) {
        String email = normalize(request.getRequesterEmail());
        if (userService.existsByEmail(email)) {
            throw new AppException(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED);
        }

        String temporaryPassword = RandomPasswordGenerator.generate();
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .email(email)
                .password(temporaryPassword)
                .fullName(normalize(request.getRequesterName()))
                .phoneNumber(normalize(request.getRequesterPhoneNumber()))
                .role(request.getRequestedRole())
                .avatarUrl(null)
                .build();

        User savedUser = userService.createUser(userRequest, UserStatus.ACTIVE);
        companyService.createCompany(
                savedUser,
                normalize(request.getOrganizationName()),
                normalize(request.getRequesterEmail()),
                normalize(request.getRequesterPhoneNumber()));
        mailService.sendNewUserCredentialsEmail(savedUser.getEmail(), savedUser.getFullName(), temporaryPassword);
    }

    private void approveAuthenticatedRequest(PartnershipRequest request) {
        User submittedByUser = request.getSubmittedByUser();
        User user = userService.getUserEntityById(submittedByUser.getId());

        user.setRole(request.getRequestedRole());
        User savedUser = userService.saveUserEntity(user);

        if (!companyService.existsByOwnerUserId(savedUser.getId())) {
            companyService.createCompany(
                    savedUser,
                    normalize(request.getOrganizationName()),
                    normalize(request.getRequesterEmail()),
                    normalize(request.getRequesterPhoneNumber()));
        }

        mailService.sendPartnershipApprovedEmail(
                resolveNotificationEmail(request, savedUser),
                resolveNotificationName(request, savedUser),
                request.getRequestedRole(),
                request.getOrganizationName());
    }

    private PartnershipRequest buildRequest(SubmitPartnershipRequest request, User submittedByUser) {
        return PartnershipRequest.builder()
                .submittedByUser(submittedByUser)
                .requesterName(normalize(request.getRequesterName()))
                .requesterEmail(normalize(request.getRequesterEmail()))
                .requesterPhoneNumber(normalize(request.getRequesterPhoneNumber()))
                .organizationName(normalize(request.getOrganizationName()))
                .requestedRole(request.getRequestedRole())
                .accountAction(submittedByUser == null
                        ? PartnershipAccountAction.CREATE_NEW_ACCOUNT
                        : PartnershipAccountAction.UPGRADE_EXISTING_USER)
                .message(normalize(request.getMessage()))
                .acceptedPolicy(Boolean.TRUE)
                .status(PartnershipRequestStatus.PENDING)
                .build();
    }

    private void validateSubmission(SubmitPartnershipRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getAcceptedPolicy())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        validateRequestedRole(request.getRequestedRole());
    }

    private void validateRequestedRole(Role requestedRole) {
        if (requestedRole != Role.EXHIBITOR && requestedRole != Role.ORGANIZER) {
            throw new AppException(ErrorCode.INVALID_PARTNERSHIP_ROLE);
        }
    }

    private String resolveNotificationEmail(PartnershipRequest request) {
        User submittedByUser = request.getSubmittedByUser();
        if (submittedByUser == null || submittedByUser.getEmail() == null) {
            return request.getRequesterEmail();
        }
        if (request.getRequesterEmail() == null
                || request.getRequesterEmail().equalsIgnoreCase(submittedByUser.getEmail())) {
            return submittedByUser.getEmail();
        }
        return request.getRequesterEmail();
    }

    private String resolveNotificationEmail(PartnershipRequest request, User savedUser) {
        if (request.getRequesterEmail() == null || request.getRequesterEmail().equalsIgnoreCase(savedUser.getEmail())) {
            return savedUser.getEmail();
        }
        return request.getRequesterEmail();
    }

    private String resolveNotificationName(PartnershipRequest request, String notificationEmail) {
        User submittedByUser = request.getSubmittedByUser();
        if (submittedByUser != null
                && submittedByUser.getEmail() != null
                && submittedByUser.getEmail().equalsIgnoreCase(notificationEmail)
                && submittedByUser.getFullName() != null) {
            return submittedByUser.getFullName();
        }
        return request.getRequesterName();
    }

    private String resolveNotificationName(PartnershipRequest request, User savedUser) {
        if (request.getRequesterEmail() == null || request.getRequesterEmail().equalsIgnoreCase(savedUser.getEmail())) {
            return savedUser.getFullName();
        }
        return request.getRequesterName();
    }

    private User getCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userService.getUserEntityById(currentUser.getId());
    }

    private PartnershipRequest getRequest(UUID id) {
        return partnershipRequestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARTNERSHIP_REQUEST_NOT_FOUND));
    }

    private PartnershipRequest getPendingRequest(UUID id) {
        PartnershipRequest request = getRequest(id);
        if (request.getStatus() != PartnershipRequestStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_PARTNERSHIP_REQUEST_STATUS);
        }
        return request;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    @Transactional
    public String verifyRequest(String encryptedToken) {
        String rawToken;
        try {
            rawToken = TokenEncryptionUtils.decrypt(encryptedToken);
        } catch (Exception e) {
            return partnershipFrontendUrl + "?partnership_error=invalid_token";
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(rawToken);
        } catch (Exception e) {
            return partnershipFrontendUrl + "?partnership_error=invalid_token";
        }

        Optional<PartnershipRequest> requestOpt = partnershipRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return partnershipFrontendUrl + "?partnership_error=not_found";
        }

        PartnershipRequest request = requestOpt.get();
        if (request.getStatus() != PartnershipRequestStatus.AWAITING_VERIFICATION) {
            return partnershipFrontendUrl + "?partnership_error=already_processed";
        }

        // Check if expired (24 hours)
        if (request.getCreatedAt() != null &&
                Duration.between(request.getCreatedAt(), Instant.now()).toHours() >= 24) {
            return partnershipFrontendUrl + "?partnership_error=expired";
        }

        request.setStatus(PartnershipRequestStatus.PENDING);
        partnershipRequestRepository.save(request);
        return partnershipFrontendUrl + "?partnership_confirmed=true";
    }

    private void sendVerificationEmail(PartnershipRequest request) {
        String encryptedToken = TokenEncryptionUtils.encrypt(request.getId().toString());
        String confirmUrl = backendBaseUrl + "/api/v1/partnership-requests/verify?token=" + encryptedToken;

        mailService.sendPartnershipVerificationEmail(
                request.getRequesterEmail(),
                request.getRequesterName(),
                request.getOrganizationName(),
                confirmUrl);
    }
}
