package com.example.vex360.features.designrequest.services;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Applies the approved-request policy that retains only the active snapshot.
 */
@Service
@RequiredArgsConstructor
public class DesignDraftRetentionService {
    private final DesignDraftRepository designDraftRepository;
    private final DesignRequestRepository designRequestRepository;

    @Transactional
    public void retainApprovedDraft(DesignRequest request, DesignDraft approvedDraft) {
        if (!belongsToRequest(request, approvedDraft)) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_GRAPH_INVALID);
        }
        if (approvedDraft.getVersionNumber() == null || approvedDraft.getVersionNumber() <= 0) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_VERSION_INVALID);
        }
        if (request.getDrafts().stream().noneMatch(draft -> sameDraft(draft, approvedDraft))) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_NOT_FOUND);
        }

        request.getDrafts().removeIf(draft -> draft.getVersionNumber() != null && draft.getVersionNumber() <= 0);
        List<DesignRequest> previousApprovedRequests = designRequestRepository
                .findByBoothIdAndStatusAndIdNot(
                        request.getBooth().getId(),
                        DesignRequestStatus.APPROVED,
                        request.getId());
        previousApprovedRequests.forEach(previous -> previous.getDrafts().clear());

        // Materialize orphan removal before reference-aware asset cleanup runs.
        designDraftRepository.flush();
    }

    private boolean belongsToRequest(DesignRequest request, DesignDraft draft) {
        if (request == null || draft == null || draft.getDesignRequest() == null) {
            return false;
        }
        return draft.getDesignRequest() == request
                || request.getId() != null
                        && Objects.equals(draft.getDesignRequest().getId(), request.getId());
    }

    private boolean sameDraft(DesignDraft left, DesignDraft right) {
        return left == right
                || left != null && right != null && left.getId() != null
                        && left.getId().equals(right.getId());
    }
}
