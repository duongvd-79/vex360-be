package com.example.vex360.features.designrequest.services;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.shared.enums.DesignRequestStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignRequestLifecycleService {

    private final DesignRequestRepository designRequestRepository;
    private final DesignDraftRepository designDraftRepository;
    private final DesignDraftAssetService designDraftAssetService;

    private static final List<DesignRequestStatus> OPEN_STATUSES = List.of(
            DesignRequestStatus.PENDING,
            DesignRequestStatus.ASSIGNED,
            DesignRequestStatus.DRAFT_SUBMITTED,
            DesignRequestStatus.REVISION_REQUESTED);

    @Transactional
    public void handleExhibitionLifecycleTransition(Integer exhibitionId) {
        List<DesignRequest> openRequests = designRequestRepository
                .findOpenByExhibitionId(exhibitionId, OPEN_STATUSES);

        for (DesignRequest request : openRequests) {
            request.setStatus(DesignRequestStatus.CANCELED);
            request.setCanceledAt(Instant.now());
            request.setCancellationStatus(DesignRequestCancellationStatus.APPROVED);
            request.setCancellationReason("EXHIBITION_LIFECYCLE");
            request.setQuotaCharged(false);
            request.getBooth().setStatus(BoothStatus.DRAFT);
            request.getDrafts().clear();
        }
        if (!openRequests.isEmpty()) {
            designDraftRepository.flush();
            openRequests.forEach(designDraftAssetService::cleanupAfterApproval);
        }
    }
}
