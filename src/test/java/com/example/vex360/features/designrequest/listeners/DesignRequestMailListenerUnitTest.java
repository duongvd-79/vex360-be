package com.example.vex360.features.designrequest.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.company.entities.Company;
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

@ExtendWith(MockitoExtension.class)
class DesignRequestMailListenerUnitTest {

    @Mock
    private DesignRequestRepository designRequestRepository;

    @Mock
    private DesignDraftRepository designDraftRepository;

    @Mock
    private MailService mailService;

    @InjectMocks
    private DesignRequestMailListener listener;

    private UUID requestId;
    private User requestedBy;
    private User designer;
    private Company company;
    private Booth booth;
    private DesignRequest designRequest;
    private DesignDraft draft;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();

        requestedBy = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
                .fullName("Exhibitor User")
                .build();

        designer = User.builder()
                .id(UUID.randomUUID())
                .email("designer@example.com")
                .fullName("Designer User")
                .build();

        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Alpha Corp")
                .build();

        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth 3D")
                .build();

        designRequest = DesignRequest.builder()
                .id(requestId)
                .requestedBy(requestedBy)
                .assignedDesigner(designer)
                .company(company)
                .booth(booth)
                .status(DesignRequestStatus.APPROVED)
                .cancellationStatus(DesignRequestCancellationStatus.NONE)
                .build();

        draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(designRequest)
                .versionNumber(2)
                .rejectionReason("Fix color scheme")
                .build();
    }

    @Test
    void handleDesignRequestStatusChanged_Approved_SendsEmailToDesigner() {
        when(designRequestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));

        DesignRequestStatusChangedEvent event = new DesignRequestStatusChangedEvent(
                requestId, company.getId(), designer.getId(), null, DesignRequestStatus.DRAFT_SUBMITTED, DesignRequestStatus.APPROVED);

        listener.handleDesignRequestStatusChanged(event);

        verify(mailService).sendDesignDraftReviewResultEmail(
                eq("designer@example.com"),
                eq("Designer User"),
                eq("Alpha Corp"),
                eq("Booth 3D"),
                eq(2),
                eq(DesignRequestStatus.APPROVED),
                isNull());
    }

    @Test
    void handleDesignRequestStatusChanged_RevisionRequested_SendsEmailWithReviewNote() {
        designRequest.setStatus(DesignRequestStatus.REVISION_REQUESTED);

        when(designRequestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));
        when(designDraftRepository.findFirstByDesignRequestIdOrderByVersionNumberDesc(requestId))
                .thenReturn(Optional.of(draft));

        DesignRequestStatusChangedEvent event = new DesignRequestStatusChangedEvent(
                requestId, company.getId(), designer.getId(), null, DesignRequestStatus.DRAFT_SUBMITTED, DesignRequestStatus.REVISION_REQUESTED);

        listener.handleDesignRequestStatusChanged(event);

        verify(mailService).sendDesignDraftReviewResultEmail(
                eq("designer@example.com"),
                eq("Designer User"),
                eq("Alpha Corp"),
                eq("Booth 3D"),
                eq(2),
                eq(DesignRequestStatus.REVISION_REQUESTED),
                eq("Fix color scheme"));
    }

    @Test
    void handleDesignRequestStatusChanged_OtherStatus_IgnoresEvent() {
        DesignRequestStatusChangedEvent event = new DesignRequestStatusChangedEvent(
                requestId, company.getId(), designer.getId(), null, DesignRequestStatus.PENDING, DesignRequestStatus.ASSIGNED);

        listener.handleDesignRequestStatusChanged(event);

        verify(mailService, never()).sendDesignDraftReviewResultEmail(anyString(), anyString(), anyString(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void handleDesignRequestCancellationChanged_Approved_SendsToExhibitorAndDesigner() {
        Instant now = Instant.now();
        designRequest.setCancellationStatus(DesignRequestCancellationStatus.APPROVED);
        designRequest.setCancellationReason("Change of plan");
        designRequest.setCancellationResolutionNote("Approved by admin");
        designRequest.setCancellationResolvedAt(now);

        when(designRequestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        DesignRequestCancellationChangedEvent event = new DesignRequestCancellationChangedEvent(
                requestId, company.getId(), designer.getId(), null, DesignRequestCancellationStatus.APPROVED);

        listener.handleDesignRequestCancellationChanged(event);

        // Exhibitor email
        verify(mailService).sendDesignCancellationDecisionEmail(
                eq("exhibitor@example.com"),
                eq("Exhibitor User"),
                eq(false),
                eq("Alpha Corp"),
                eq("Booth 3D"),
                eq("APPROVED"),
                eq("Change of plan"),
                eq("Approved by admin"),
                eq(now));

        // Designer email
        verify(mailService).sendDesignCancellationDecisionEmail(
                eq("designer@example.com"),
                eq("Designer User"),
                eq(true),
                eq("Alpha Corp"),
                eq("Booth 3D"),
                eq("APPROVED"),
                eq("Change of plan"),
                eq("Approved by admin"),
                eq(now));
    }

    @Test
    void handleDesignRequestCancellationChanged_NoAssignedDesigner_SendsOnlyToExhibitor() {
        designRequest.setAssignedDesigner(null);
        designRequest.setCancellationStatus(DesignRequestCancellationStatus.REJECTED);
        designRequest.setCancellationReason("Duplicate request");
        designRequest.setCancellationResolutionNote("Rejected");

        when(designRequestRepository.findById(requestId)).thenReturn(Optional.of(designRequest));

        DesignRequestCancellationChangedEvent event = new DesignRequestCancellationChangedEvent(
                requestId, company.getId(), null, null, DesignRequestCancellationStatus.REJECTED);

        listener.handleDesignRequestCancellationChanged(event);

        // Exhibitor email
        verify(mailService).sendDesignCancellationDecisionEmail(
                eq("exhibitor@example.com"),
                eq("Exhibitor User"),
                eq(false),
                eq("Alpha Corp"),
                eq("Booth 3D"),
                eq("REJECTED"),
                eq("Duplicate request"),
                eq("Rejected"),
                isNull());

        // Designer email not sent
        verify(mailService, never()).sendDesignCancellationDecisionEmail(anyString(), anyString(), eq(true), anyString(), anyString(), any(), anyString(), anyString(), any());
    }
}
