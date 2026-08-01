package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestLifecycleService;
import com.example.vex360.shared.enums.DesignRequestStatus;

@ExtendWith(MockitoExtension.class)
class DesignRequestLifecycleServiceUnitTest {
    @Mock
    DesignRequestRepository requestRepository;
    @Mock
    DesignDraftRepository draftRepository;
    @Mock
    DesignDraftAssetService assetService;

    @Test
    void lifecycleCancellationRefundsQuotaClearsDraftsAndAssets() {
        DesignRequest request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .status(DesignRequestStatus.ASSIGNED)
                .quotaCharged(true)
                .build();
        request.getDrafts().add(DesignDraft.builder().versionNumber(0).designRequest(request).build());
        when(requestRepository.findOpenByExhibitionId(1, List.of(
                DesignRequestStatus.PENDING,
                DesignRequestStatus.ASSIGNED,
                DesignRequestStatus.DRAFT_SUBMITTED,
                DesignRequestStatus.REVISION_REQUESTED)))
                .thenReturn(List.of(request));
        DesignRequestLifecycleService service = new DesignRequestLifecycleService(
                requestRepository, draftRepository, assetService);

        service.handleExhibitionLifecycleTransition(1);

        assertFalse(request.getQuotaCharged());
        assertTrue(request.getDrafts().isEmpty());
        verify(draftRepository).flush();
        verify(assetService).cleanupAfterApproval(request);
    }
}
