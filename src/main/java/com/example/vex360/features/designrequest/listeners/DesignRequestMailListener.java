package com.example.vex360.features.designrequest.listeners;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.events.DesignRequestCancellationChangedEvent;
import com.example.vex360.features.designrequest.events.DesignRequestStatusChangedEvent;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DesignRequestMailListener {

    private final DesignRequestRepository designRequestRepository;
    private final DesignDraftRepository designDraftRepository;
    private final MailService mailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleDesignRequestStatusChanged(DesignRequestStatusChangedEvent event) {
        if (event == null || event.requestId() == null) {
            return;
        }

        DesignRequestStatus status = event.status();
        DesignRequestStatus previousStatus = event.previousStatus();

        boolean isApproved = status == DesignRequestStatus.APPROVED;
        boolean isRevisionRequested = previousStatus == DesignRequestStatus.DRAFT_SUBMITTED
                && status == DesignRequestStatus.REVISION_REQUESTED;

        if (!isApproved && !isRevisionRequested) {
            return;
        }

        log.info("Handling DesignRequestStatusChangedEvent for request ID: {}, status: {}", event.requestId(), status);

        try {
            DesignRequest designRequest = designRequestRepository.findById(event.requestId()).orElse(null);
            if (designRequest == null) {
                log.warn("DesignRequest not found for ID {} in mail listener", event.requestId());
                return;
            }

            User designer = designRequest.getAssignedDesigner();
            if (designer == null || designer.getEmail() == null || designer.getEmail().isBlank()) {
                log.warn("Assigned designer or email missing for DesignRequest ID {}", event.requestId());
                return;
            }

            DesignDraft latestDraft = designDraftRepository
                    .findFirstByDesignRequestIdOrderByVersionNumberDesc(event.requestId())
                    .orElse(null);

            String companyName = designRequest.getCompany() != null ? designRequest.getCompany().getName() : "";
            String boothName = designRequest.getBooth() != null ? designRequest.getBooth().getName() : "";
            Integer versionNumber = latestDraft != null ? latestDraft.getVersionNumber() : null;
            String reviewNote = (isRevisionRequested && latestDraft != null) ? latestDraft.getRejectionReason() : null;

            mailService.sendDesignDraftReviewResultEmail(
                    designer.getEmail(),
                    designer.getFullName(),
                    companyName,
                    boothName,
                    versionNumber,
                    status,
                    reviewNote);
        } catch (Exception e) {
            log.error("Failed to handle DesignRequestStatusChangedEvent mail notification for ID {}: {}", event.requestId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void handleDesignRequestCancellationChanged(DesignRequestCancellationChangedEvent event) {
        if (event == null || event.requestId() == null) {
            return;
        }

        DesignRequestCancellationStatus status = event.status();
        if (status != DesignRequestCancellationStatus.APPROVED && status != DesignRequestCancellationStatus.REJECTED) {
            return;
        }

        log.info("Handling DesignRequestCancellationChangedEvent for request ID: {}, cancellation status: {}", event.requestId(), status);

        try {
            DesignRequest designRequest = designRequestRepository.findById(event.requestId()).orElse(null);
            if (designRequest == null) {
                log.warn("DesignRequest not found for ID {} in cancellation mail listener", event.requestId());
                return;
            }

            User requestedBy = designRequest.getRequestedBy();
            User assignedDesigner = designRequest.getAssignedDesigner();

            String companyName = designRequest.getCompany() != null ? designRequest.getCompany().getName() : "";
            String boothName = designRequest.getBooth() != null ? designRequest.getBooth().getName() : "";
            String cancellationReason = designRequest.getCancellationReason();
            String resolutionNote = designRequest.getCancellationResolutionNote();
            java.time.Instant resolvedAt = designRequest.getCancellationResolvedAt();

            String statusStr = status != null ? status.name() : null;

            // Send to Exhibitor (requestedBy)
            if (requestedBy != null && requestedBy.getEmail() != null && !requestedBy.getEmail().isBlank()) {
                mailService.sendDesignCancellationDecisionEmail(
                        requestedBy.getEmail(),
                        requestedBy.getFullName(),
                        false,
                        companyName,
                        boothName,
                        statusStr,
                        cancellationReason,
                        resolutionNote,
                        resolvedAt);
            } else {
                log.warn("RequestedBy exhibitor user or email missing for DesignRequest ID {}", event.requestId());
            }

            // Send to Designer (assignedDesigner) if assigned
            if (assignedDesigner != null && assignedDesigner.getEmail() != null && !assignedDesigner.getEmail().isBlank()) {
                mailService.sendDesignCancellationDecisionEmail(
                        assignedDesigner.getEmail(),
                        assignedDesigner.getFullName(),
                        true,
                        companyName,
                        boothName,
                        statusStr,
                        cancellationReason,
                        resolutionNote,
                        resolvedAt);
            }
        } catch (Exception e) {
            log.error("Failed to handle DesignRequestCancellationChangedEvent mail notification for ID {}: {}", event.requestId(), e.getMessage());
        }
    }
}
