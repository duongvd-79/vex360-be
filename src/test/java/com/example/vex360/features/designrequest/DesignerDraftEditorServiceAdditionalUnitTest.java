package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.dtos.request.ReorderDesignDraftPanoramasRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftHotspotRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftMediaAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftGraphValidator;
import com.example.vex360.features.designrequest.services.DesignRequestMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.designrequest.services.DesignerDraftEditorService;
import com.example.vex360.features.designrequest.services.DesignerDraftPreviewService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignerDraftEditorServiceAdditionalUnitTest {
    @Mock
    private DesignRequestRepository requestRepository;
    @Mock
    private DesignDraftRepository draftRepository;
    @Mock
    private DesignDraftAssetRepository draftAssetRepository;
    @Mock
    private DesignDraftPanoramaRepository draftPanoramaRepository;
    @Mock
    private DesignDraftHotspotRepository draftHotspotRepository;
    @Mock
    private DesignDraftMediaAssetRepository draftMediaAssetRepository;
    @Mock
    private DesignDraftAssetService assetService;
    @Mock
    private DesignRequestProductService requestProductService;
    @Mock
    private DesignRequestMediaAssetService requestMediaAssetService;
    @Mock
    private ProductService productService;
    @Mock
    private BoothDesignService boothDesignService;
    @Mock
    private DesignDraftBenefitGuardService benefitGuardService;
    @Mock
    private DesignDraftGraphValidator graphValidator;
    @Mock
    private DesignerDraftPreviewService previewService;
    @Mock
    private DesignRequestMapper designRequestMapper;
    @Mock
    private DesignRequestLifecyclePolicy lifecyclePolicy;

    private DesignerDraftEditorService service;
    private User designer;
    private DesignRequest request;
    private DesignDraft draft;

    @BeforeEach
    void setup() {
        service = new DesignerDraftEditorService(
                requestRepository,
                draftRepository,
                draftAssetRepository,
                draftPanoramaRepository,
                draftHotspotRepository,
                draftMediaAssetRepository,
                assetService,
                requestProductService,
                requestMediaAssetService,
                productService,
                boothDesignService,
                benefitGuardService,
                graphValidator,
                previewService,
                designRequestMapper,
                lifecyclePolicy);
        designer = User.builder().id(UUID.randomUUID()).build();
        Company company = Company.builder().id(UUID.randomUUID()).build();
        Booth booth = Booth.builder().id(UUID.randomUUID()).company(company).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(booth)
                .assignedDesigner(designer)
                .status(DesignRequestStatus.ASSIGNED)
                .build();
        draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(0)
                .build();
        request.getDrafts().add(draft);
        when(requestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(draftRepository.findByDesignRequestIdAndVersionNumber(request.getId(), 0))
                .thenReturn(Optional.of(draft));
    }

    @Test
    void updatePanoramaChangesAssetNameAndDefault() {
        DesignDraftPanorama first = panorama("First", 0, true);
        DesignDraftPanorama updated = panorama("Old", 1, false);
        draft.getPanoramas().addAll(List.of(first, updated));
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .designRequest(request)
                .assetType(DesignDraftAssetType.PANORAMA)
                .url("https://cdn/new.jpg")
                .publicId("panorama/new")
                .build();
        when(assetService.requireDraftAsset(request, assetId, DesignDraftAssetType.PANORAMA))
                .thenReturn(asset);

        service.updatePanorama(
                designer,
                request.getId(),
                updated.getId(),
                0L,
                new UpdateDesignDraftPanoramaRequest("  Updated  ", assetId, true));

        assertEquals("Updated", updated.getName());
        assertEquals("https://cdn/new.jpg", updated.getImageUrl());
        assertEquals("panorama/new", updated.getImageKey());
        assertTrue(updated.getIsDefault());
        assertFalse(first.getIsDefault());
        verify(graphValidator).validateWorkingGraph(request, draft);
        verify(draftRepository).saveAndFlush(draft);
    }

    @Test
    void updatePanoramaRejectsNullUpdate() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.updatePanorama(designer, request.getId(), UUID.randomUUID(), 0L, null));

        assertSame(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID, exception.getErrorCode());
    }

    @Test
    void reorderPanoramasPersistsRequestedOrder() {
        DesignDraftPanorama first = panorama("First", 0, true);
        DesignDraftPanorama second = panorama("Second", 1, false);
        draft.getPanoramas().addAll(List.of(first, second));

        service.reorderPanoramas(
                designer,
                request.getId(),
                0L,
                new ReorderDesignDraftPanoramasRequest(List.of(second.getId(), first.getId())));

        assertEquals(0, second.getOrderIndex());
        assertEquals(1, first.getOrderIndex());
        verify(graphValidator).validateWorkingGraph(request, draft);
        verify(draftRepository).saveAndFlush(draft);
    }

    @Test
    void reorderPanoramasRejectsDuplicateOrIncompleteIds() {
        DesignDraftPanorama first = panorama("First", 0, true);
        DesignDraftPanorama second = panorama("Second", 1, false);
        draft.getPanoramas().addAll(List.of(first, second));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.reorderPanoramas(
                        designer,
                        request.getId(),
                        0L,
                        new ReorderDesignDraftPanoramasRequest(List.of(first.getId(), first.getId()))));

        assertSame(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID, exception.getErrorCode());
    }

    private DesignDraftPanorama panorama(String name, int order, boolean isDefault) {
        return DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .clientKey(UUID.randomUUID().toString())
                .name(name)
                .imageUrl("https://cdn/panorama.jpg")
                .imageKey("panorama/" + UUID.randomUUID())
                .orderIndex(order)
                .isDefault(isDefault)
                .build();
    }
}
