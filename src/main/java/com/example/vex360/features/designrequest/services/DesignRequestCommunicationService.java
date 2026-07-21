package com.example.vex360.features.designrequest.services;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestMessageResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMessage;
import com.example.vex360.features.designrequest.events.DesignRequestMessageCreatedEvent;
import com.example.vex360.features.designrequest.repositories.DesignRequestMessageRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Service that handles the communication thread (messages) for a DesignRequest.
 * Enforces security access constraints based on user roles (Exhibitor,
 * Designer, Admin).
 */
@Service
@RequiredArgsConstructor
public class DesignRequestCommunicationService {
    private final DesignRequestRepository requestRepository;
    private final DesignRequestMessageRepository messageRepository;
    private final CompanyService companyService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Retrieves messages for an Exhibitor-owned design request with pagination
     * support.
     * Enforces that the request belongs to the Exhibitor's company.
     *
     * @param user      authenticated Exhibitor
     * @param requestId the design request identifier
     * @param pageable  pagination options
     * @return page response of clarification messages
     * @throws AppException if unauthorized or request not found
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestMessageResponseDTO> getForExhibitor(
            User user, UUID requestId, Pageable pageable) {
        requireExhibitorAccess(user, requestId);
        return getMessages(requestId, pageable);
    }

    /**
     * Retrieves messages for a Designer's assigned design request with pagination
     * support.
     * Enforces that the request is assigned to the Designer.
     *
     * @param user      authenticated Designer
     * @param requestId the design request identifier
     * @param pageable  pagination options
     * @return page response of clarification messages
     * @throws AppException if unauthorized or request not found
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestMessageResponseDTO> getForDesigner(
            User user, UUID requestId, Pageable pageable) {
        requireDesignerAccess(user, requestId);
        return getMessages(requestId, pageable);
    }

    /**
     * Retrieves messages for a design request for Admin review.
     *
     * @param requestId the design request identifier
     * @param pageable  pagination options
     * @return page response of clarification messages
     * @throws AppException if request not found
     */
    @Transactional(readOnly = true)
    public PageResponse<DesignRequestMessageResponseDTO> getForAdmin(UUID requestId, Pageable pageable) {
        requireRequest(requestId);
        return getMessages(requestId, pageable);
    }

    /**
     * Sends a clarification message from the Exhibitor on a design request thread.
     *
     * @param user      authenticated Exhibitor
     * @param requestId the design request identifier
     * @param text      message content
     * @return the saved message DTO
     * @throws AppException if validation or access check fails
     */
    @Transactional
    public DesignRequestMessageResponseDTO sendForExhibitor(User user, UUID requestId, String text) {
        return send(requireExhibitorAccess(user, requestId), user, text);
    }

    /**
     * Sends a clarification message from the Designer on an assigned design request
     * thread.
     *
     * @param user      authenticated Designer
     * @param requestId the design request identifier
     * @param text      message content
     * @return the saved message DTO
     * @throws AppException if validation or access check fails
     */
    @Transactional
    public DesignRequestMessageResponseDTO sendForDesigner(User user, UUID requestId, String text) {
        return send(requireDesignerAccess(user, requestId), user, text);
    }

    private DesignRequestMessageResponseDTO send(DesignRequest request, User sender, String text) {
        String messageText = text == null ? null : text.trim();
        if (messageText == null || messageText.isEmpty() || messageText.length() > 2000) {
            throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
        }
        DesignRequestMessage message = messageRepository.save(DesignRequestMessage.builder()
                .designRequest(request)
                .sender(sender)
                .message(messageText)
                .build());
        eventPublisher.publishEvent(new DesignRequestMessageCreatedEvent(
                request.getId(),
                request.getCompany().getId(),
                request.getAssignedDesigner() == null ? null : request.getAssignedDesigner().getId(),
                sender.getId(),
                message.getId()));
        return toResponse(message);
    }

    private PageResponse<DesignRequestMessageResponseDTO> getMessages(UUID requestId, Pageable pageable) {
        return PageResponse.from(messageRepository.findByDesignRequestId(requestId, pageable).map(this::toResponse));
    }

    private DesignRequest requireExhibitorAccess(User user, UUID requestId) {
        Company company = companyService.getCompanyEntityForCurrentUser(user);
        DesignRequest request = requireRequest(requestId);
        if (!request.getCompany().getId().equals(company.getId())) {
            throw new AppException(ErrorCode.DESIGN_MESSAGE_NOT_ALLOWED);
        }
        return request;
    }

    private DesignRequest requireDesignerAccess(User user, UUID requestId) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        DesignRequest request = requireRequest(requestId);
        if (request.getAssignedDesigner() == null
                || !user.getId().equals(request.getAssignedDesigner().getId())) {
            throw new AppException(ErrorCode.DESIGN_MESSAGE_NOT_ALLOWED);
        }
        return request;
    }

    private DesignRequest requireRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.DESIGN_REQUEST_NOT_FOUND));
    }

    private DesignRequestMessageResponseDTO toResponse(DesignRequestMessage message) {
        return new DesignRequestMessageResponseDTO(
                message.getId(),
                message.getDesignRequest().getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getSender().getRole(),
                message.getMessage(),
                message.getCreatedAt());
    }
}
