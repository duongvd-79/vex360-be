package com.example.vex360.features.partnership.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.partnership.dtos.request.RejectPartnershipRequest;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.repositories.PartnershipRequestRepository;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.PartnershipRequest;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.AuthProvider;
import com.example.vex360.shared.enums.CompanyStatus;
import com.example.vex360.shared.enums.PartnershipRequestStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.utils.RandomPasswordGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartnershipRequestService {
    private final PartnershipRequestRepository partnershipRequestRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Transactional
    public PartnershipRequestResponseDTO submitGuestRequest(SubmitPartnershipRequest request) {
        validateSubmission(request);
        String requesterEmail = normalize(request.getRequesterEmail());

        if (userRepository.existsByEmail(requesterEmail)) {
            throw new AppException(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED);
        }
        if (partnershipRequestRepository.existsByRequesterEmailAndStatus(
                requesterEmail,
                PartnershipRequestStatus.PENDING)) {
            throw new AppException(ErrorCode.PARTNERSHIP_REQUEST_ALREADY_PENDING);
        }

        PartnershipRequest partnershipRequest = buildRequest(request, null);
        return toResponse(partnershipRequestRepository.save(partnershipRequest));
    }

    @Transactional
    public PartnershipRequestResponseDTO submitAuthenticatedRequest(User currentUser, SubmitPartnershipRequest request) {
        validateSubmission(request);
        User submittedByUser = getCurrentUser(currentUser);
        String requesterEmail = normalize(request.getRequesterEmail());

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

        PartnershipRequest partnershipRequest = buildRequest(request, submittedByUser);
        return toResponse(partnershipRequestRepository.save(partnershipRequest));
    }

    @Transactional(readOnly = true)
    public PageResponse<PartnershipRequestResponseDTO> getRequests(
            PartnershipRequestStatus status,
            Role requestedRole,
            Pageable pageable) {
        if (requestedRole != null) {
            validateRequestedRole(requestedRole);
        }

        Page<PartnershipRequestResponseDTO> requests = partnershipRequestRepository
                .searchRequests(status, requestedRole, pageable)
                .map(this::toResponse);
        return PageResponse.from(requests);
    }

    @Transactional(readOnly = true)
    public PartnershipRequestResponseDTO getRequestById(UUID id) {
        return toResponse(getRequest(id));
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
        request.setReviewedAt(LocalDateTime.now());
        return toResponse(partnershipRequestRepository.save(request));
    }

    @Transactional
    public PartnershipRequestResponseDTO rejectRequest(UUID id, RejectPartnershipRequest rejectRequest) {
        PartnershipRequest request = getPendingRequest(id);
        request.setStatus(PartnershipRequestStatus.REJECTED);
        request.setReviewNote(rejectRequest.getReviewNote());
        request.setReviewedAt(LocalDateTime.now());
        return toResponse(partnershipRequestRepository.save(request));
    }

    private void approveGuestRequest(PartnershipRequest request) {
        String email = normalize(request.getRequesterEmail());
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.PARTNERSHIP_EMAIL_ALREADY_REGISTERED);
        }

        String temporaryPassword = RandomPasswordGenerator.generate();
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(temporaryPassword))
                .fullName(normalize(request.getRequesterName()))
                .phoneNumber(normalize(request.getRequesterPhoneNumber()))
                .role(request.getRequestedRole())
                .provider(AuthProvider.LOCAL)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        createCompany(savedUser, request);
        mailService.sendNewUserCredentialsEmail(savedUser.getEmail(), savedUser.getFullName(), temporaryPassword);
    }

    private void approveAuthenticatedRequest(PartnershipRequest request) {
        User submittedByUser = request.getSubmittedByUser();
        User user = userRepository.findById(submittedByUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setRole(request.getRequestedRole());
        User savedUser = userRepository.save(user);

        if (!companyRepository.existsByOwnerUserId(savedUser.getId())) {
            createCompany(savedUser, request);
        }

        mailService.sendPartnershipApprovedEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                request.getRequestedRole(),
                request.getOrganizationName());
    }

    private Company createCompany(User ownerUser, PartnershipRequest request) {
        Company company = Company.builder()
                .ownerUser(ownerUser)
                .name(normalize(request.getOrganizationName()))
                .email(normalize(request.getRequesterEmail()))
                .status(CompanyStatus.INCOMPLETE_PROFILE)
                .build();
        return companyRepository.save(company);
    }

    private PartnershipRequest buildRequest(SubmitPartnershipRequest request, User submittedByUser) {
        return PartnershipRequest.builder()
                .submittedByUser(submittedByUser)
                .requesterName(normalize(request.getRequesterName()))
                .requesterEmail(normalize(request.getRequesterEmail()))
                .requesterPhoneNumber(normalize(request.getRequesterPhoneNumber()))
                .organizationName(normalize(request.getOrganizationName()))
                .requestedRole(request.getRequestedRole())
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

    private User getCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
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

    private PartnershipRequestResponseDTO toResponse(PartnershipRequest request) {
        User submittedByUser = request.getSubmittedByUser();
        return new PartnershipRequestResponseDTO(
                request.getId(),
                submittedByUser == null ? null : submittedByUser.getId(),
                submittedByUser == null ? null : submittedByUser.getEmail(),
                request.getRequesterName(),
                request.getRequesterEmail(),
                request.getRequesterPhoneNumber(),
                request.getOrganizationName(),
                request.getRequestedRole().name(),
                request.getMessage(),
                request.getAcceptedPolicy(),
                request.getStatus().name(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt());
    }
}
