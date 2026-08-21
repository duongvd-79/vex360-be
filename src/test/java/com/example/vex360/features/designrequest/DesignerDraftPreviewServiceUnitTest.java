package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftBenefitUsageResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftPreviewResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.enums.DesignDraftPreviewSource;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignerDraftPreviewService;
import com.example.vex360.features.designrequest.services.DesignerWorkspaceService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;

@ExtendWith(MockitoExtension.class)
class DesignerDraftPreviewServiceUnitTest {
    @Mock
    DesignerWorkspaceService workspaceService;
    @Mock
    DesignDraftRepository draftRepository;
    @Mock
    BoothMapper boothMapper;
    @Mock
    DesignDraftBenefitGuardService benefitGuardService;

    private DesignerDraftPreviewService service;
    private User designer;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignerDraftPreviewService(
                workspaceService,
                draftRepository,
                boothMapper,
                benefitGuardService);
        designer = User.builder().id(UUID.randomUUID()).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(Booth.builder().id(UUID.randomUUID()).name("Official booth").build())
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .build();
        when(workspaceService.getAssignedRequest(designer, request.getId())).thenReturn(request);
    }

    @Test
    void defaultPreviewMapsWorkingDraftNavigationToDraftPanoramaId() {
        DesignDraftAsset thumbnail = DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .url("https://cdn/baseline-thumbnail.jpg")
                .build();
        DesignDraft draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(0)
                .boothName("Working booth")
                .displayTemplateKey("classic")
                .thumbnailAction(DesignDraftFileAction.KEEP)
                .thumbnailAsset(thumbnail)
                .build();
        DesignDraftPanorama first = panorama(draft, "first", 0, true);
        DesignDraftPanorama second = panorama(draft, "second", 1, false);
        first.getHotspots().add(DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(first)
                .type(com.example.vex360.features.booth.enums.HotspotType.NAV)
                .name("Go second")
                .targetDraftPanoramaKey("second")
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build());
        draft.getPanoramas().add(first);
        draft.getPanoramas().add(second);
        DesignDraftBenefitUsageResponseDTO usage = new DesignDraftBenefitUsageResponseDTO();
        when(draftRepository.findByDesignRequestIdAndVersionNumber(request.getId(), 0))
                .thenReturn(Optional.of(draft));
        when(draftRepository.findFirstByDesignRequestIdAndVersionNumberGreaterThanOrderByVersionNumberDesc(
                request.getId(), 0)).thenReturn(Optional.empty());
        when(benefitGuardService.getUsageResponse(request, draft)).thenReturn(usage);

        DesignDraftPreviewResponseDTO response = service.getPreview(designer, request.getId(), null);

        assertEquals(DesignDraftPreviewSource.WORKING_DRAFT, response.getSource());
        assertTrue(response.getEditable());
        assertEquals(draft.getId(), response.getDraftId());
        assertSame(usage, response.getBenefitUsage());
        assertEquals("https://cdn/baseline-thumbnail.jpg", response.getBooth().getThumbnailUrl());
        assertEquals("first", response.getBooth().getPanoramas().get(0).getClientKey());
        assertEquals(
                second.getId(),
                response.getBooth().getPanoramas().get(0).getHotspots().get(0).getTargetPanoramaId());
    }

    @Test
    void currentBoothPreviewUsesBaselineBenefitUsageAndIsReadOnly() {
        BoothResponseDTO boothResponse = new BoothResponseDTO();
        boothResponse.setName("Official booth");
        boothResponse.setPanoramas(List.of(new PanoramaResponseDTO(
                UUID.randomUUID(),
                "Official panorama",
                "https://cdn/official.jpg",
                "official/key",
                0L,
                0,
                true,
                List.of())));
        DesignDraftBenefitUsageResponseDTO usage = new DesignDraftBenefitUsageResponseDTO();
        when(draftRepository.findByDesignRequestIdAndVersionNumber(request.getId(), 0))
                .thenReturn(Optional.empty());
        when(draftRepository.findFirstByDesignRequestIdAndVersionNumberGreaterThanOrderByVersionNumberDesc(
                request.getId(), 0)).thenReturn(Optional.empty());
        when(boothMapper.toBoothResponseDTO(request.getBooth())).thenReturn(boothResponse);
        when(benefitGuardService.getBaselineUsageResponse(request)).thenReturn(usage);

        DesignDraftPreviewResponseDTO response = service.getPreview(
                designer,
                request.getId(),
                DesignDraftPreviewSource.CURRENT_BOOTH);

        assertEquals(DesignDraftPreviewSource.CURRENT_BOOTH, response.getSource());
        assertFalse(response.getEditable());
        assertNull(response.getDraftId());
        assertSame(usage, response.getBenefitUsage());
        assertEquals("Official panorama", response.getBooth().getPanoramas().get(0).getName());
    }

    private DesignDraftPanorama panorama(DesignDraft draft, String key, int order, boolean isDefault) {
        return DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .clientKey(key)
                .name(key)
                .imageUrl("https://cdn/" + key + ".jpg")
                .imageKey("draft/" + key)
                .orderIndex(order)
                .isDefault(isDefault)
                .build();
    }
}
