package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.example.vex360.features.designrequest.services.DesignDraftRetentionService;
import com.example.vex360.shared.enums.DesignRequestStatus;

@ExtendWith(MockitoExtension.class)
class DesignDraftRetentionServiceUnitTest {
    @Mock
    DesignDraftRepository designDraftRepository;
    @Mock
    DesignRequestRepository designRequestRepository;

    @Test
    void approvalRetainsFinalDraftAndPurgesOlderApprovedRequestGraphs() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).build();
        DesignRequest current = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(DesignRequestStatus.DRAFT_SUBMITTED)
                .build();
        DesignDraft rejected = draft(current, 1);
        DesignDraft approved = draft(current, 2);
        current.getDrafts().add(rejected);
        current.getDrafts().add(approved);

        DesignRequest previous = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(DesignRequestStatus.APPROVED)
                .build();
        previous.getDrafts().add(draft(previous, 1));
        when(designRequestRepository.findByBoothIdAndStatusAndIdNot(
                boothId, DesignRequestStatus.APPROVED, current.getId()))
                .thenReturn(List.of(previous));

        new DesignDraftRetentionService(designDraftRepository, designRequestRepository)
                .retainApprovedDraft(current, approved);

        assertEquals(1, current.getDrafts().size());
        assertSame(approved, current.getDrafts().get(0));
        assertEquals(0, previous.getDrafts().size());
        verify(designDraftRepository).flush();
    }

    private DesignDraft draft(DesignRequest request, int versionNumber) {
        return DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(versionNumber)
                .build();
    }
}
