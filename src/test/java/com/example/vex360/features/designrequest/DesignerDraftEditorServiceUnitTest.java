package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.dtos.request.CreateDesignDraftPanoramaRequest;
import com.example.vex360.features.designrequest.dtos.request.UpdateDesignDraftSettingsRequest;
import com.example.vex360.features.designrequest.dtos.request.UpsertDesignDraftHotspotRequest;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.enums.DesignDraftFileAction;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestRepository;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftGraphValidator;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.designrequest.services.DesignRequestMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignRequestLifecyclePolicy;
import com.example.vex360.features.designrequest.services.DesignerDraftEditorService;
import com.example.vex360.features.designrequest.services.DesignerDraftPreviewService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.DesignRequestStatus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import com.example.vex360.features.designrequest.dtos.request.SubmitDesignDraftMediaAssetRequest;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.mapper.DesignRequestMapper;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignerDraftEditorServiceUnitTest {
    @Mock
    DesignRequestRepository requestRepository;
    @Mock
    DesignDraftRepository draftRepository;
    @Mock
    DesignDraftAssetRepository draftAssetRepository;
    @Mock
    DesignDraftAssetService assetService;
    @Mock
    DesignRequestProductService requestProductService;
    @Mock
    DesignRequestMediaAssetService requestMediaAssetService;
    @Mock
    ProductService productService;
    @Mock
    BoothDesignService boothDesignService;
    @Mock
    DesignDraftBenefitGuardService benefitGuardService;
    @Mock
    DesignDraftGraphValidator graphValidator;
    @Mock
    DesignerDraftPreviewService previewService;
    @Mock
    DesignRequestMapper designRequestMapper;
    @Mock
    DesignRequestLifecyclePolicy lifecyclePolicy;

    private DesignerDraftEditorService service;
    private User designer;
    private DesignRequest request;
    private DesignDraft draft;
    private DesignDraftBenefitGuardService.Usage emptyUsage;

    @BeforeEach
    void setup() {
        service = new DesignerDraftEditorService(
                requestRepository,
                draftRepository,
                draftAssetRepository,
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
        Booth booth = Booth.builder().id(UUID.randomUUID()).company(company).name("Booth").build();
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
        emptyUsage = new DesignDraftBenefitGuardService.Usage(0, 0, 0, 0);
        when(requestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        lenient().when(draftRepository.findByDesignRequestIdAndVersionNumber(request.getId(), 0))
                .thenReturn(Optional.of(draft));
    }

    @Test
    void lifecyclePolicyBlocksGranularDraftMutation() {
        doThrow(new AppException(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED))
                .when(lifecyclePolicy).assertCanContinue(request);

        AppException exception = assertThrows(AppException.class,
                () -> service.createPanorama(designer, request.getId(), null));

        assertSame(ErrorCode.BOOTH_REVIEW_DEADLINE_PASSED, exception.getErrorCode());
        verify(draftRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createPanoramaUsesRequestAssetAndMakesFirstPanoramaDefault() {
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .url("https://cdn/panorama.jpg")
                .publicId("designer/panorama")
                .assetType(DesignDraftAssetType.PANORAMA)
                .build();
        when(assetService.requireDraftAsset(request, assetId, DesignDraftAssetType.PANORAMA)).thenReturn(asset);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);

        service.createPanorama(
                designer,
                request.getId(),
                new CreateDesignDraftPanoramaRequest("Main hall", assetId, null, false));

        assertEquals(1, draft.getPanoramas().size());
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        assertEquals("Main hall", panorama.getName());
        assertEquals("https://cdn/panorama.jpg", panorama.getImageUrl());
        assertEquals("designer/panorama", panorama.getImageKey());
        assertEquals(0, panorama.getOrderIndex());
        assertTrue(panorama.getIsDefault());
        verify(graphValidator).validateWorkingGraph(request, draft);
        verify(benefitGuardService).assertMutationAllowed(request, emptyUsage, draft);
    }

    @Test
    void deletePanoramaRemovesIncomingNavigationAndNormalizesDefaultAndOrder() {
        DesignDraftPanorama first = panorama("first", 0, true);
        DesignDraftPanorama second = panorama("second", 1, false);
        DesignDraftHotspot navigation = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(first)
                .type(HotspotType.NAV)
                .name("Go second")
                .targetDraftPanoramaKey(second.getClientKey())
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
        first.getHotspots().add(navigation);
        draft.getPanoramas().add(first);
        draft.getPanoramas().add(second);

        service.deletePanorama(designer, request.getId(), second.getId());

        assertEquals(1, draft.getPanoramas().size());
        assertSame(first, draft.getPanoramas().get(0));
        assertTrue(first.getHotspots().isEmpty());
        assertTrue(first.getIsDefault());
        assertEquals(0, first.getOrderIndex());
        verify(graphValidator).validateWorkingGraph(request, draft);
    }

    @Test
    void createInfoTextHotspotStoresTextWithoutProductOrMedia() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);
        UpsertDesignDraftHotspotRequest create = new UpsertDesignDraftHotspotRequest();
        create.setType(HotspotType.INFO);
        create.setName("About us");
        create.setXPosition(1.0);
        create.setYPosition(2.0);
        create.setZPosition(3.0);
        create.setInfoContentType(HotspotInfoContentType.TEXT);
        create.setInfoText("Company introduction");

        service.createHotspot(designer, request.getId(), panorama.getId(), create);

        assertEquals(1, panorama.getHotspots().size());
        DesignDraftHotspot hotspot = panorama.getHotspots().get(0);
        assertEquals(HotspotType.INFO, hotspot.getType());
        assertEquals(HotspotInfoContentType.TEXT, hotspot.getInfoContentType());
        assertEquals("Company introduction", hotspot.getInfoText());
        assertNull(hotspot.getProduct());
        assertNull(hotspot.getMediaAsset());
        verify(benefitGuardService).assertMutationAllowed(request, emptyUsage, draft);
    }

    @Test
    void createInfoImageHotspotCanReferenceStagedMediaFromWorkingDraft() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedMedia = stagedMedia("image/png");
        draft.getMediaAssets().add(stagedMedia);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);
        UpsertDesignDraftHotspotRequest create = hotspotRequest(HotspotType.INFO);
        create.setInfoContentType(HotspotInfoContentType.IMAGE);
        create.setDesignDraftMediaAssetId(stagedMedia.getId());

        service.createHotspot(designer, request.getId(), panorama.getId(), create);

        DesignDraftHotspot hotspot = panorama.getHotspots().get(0);
        assertSame(stagedMedia, hotspot.getDesignDraftMediaAsset());
        assertNull(hotspot.getMediaAsset());
    }

    @Test
    void createHotspotRejectsOfficialAndStagedMediaIdsTogether() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedMedia = stagedMedia("image/jpeg");
        draft.getMediaAssets().add(stagedMedia);
        UpsertDesignDraftHotspotRequest create = hotspotRequest(HotspotType.INFO);
        create.setInfoContentType(HotspotInfoContentType.IMAGE);
        create.setMediaAssetId(UUID.randomUUID());
        create.setDesignDraftMediaAssetId(stagedMedia.getId());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(designer, request.getId(), panorama.getId(), create));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
        assertTrue(panorama.getHotspots().isEmpty());
    }

    @Test
    void createHotspotRejectsOfficialMediaOutsideRequestAllowlist() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        UUID mediaAssetId = UUID.randomUUID();
        UpsertDesignDraftHotspotRequest create = hotspotRequest(HotspotType.INFO);
        create.setInfoContentType(HotspotInfoContentType.IMAGE);
        create.setMediaAssetId(mediaAssetId);
        doThrow(new AppException(ErrorCode.DESIGN_MEDIA_ASSET_NOT_ALLOWED))
                .when(requestMediaAssetService).assertMediaAssetAllowed(request, mediaAssetId);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.createHotspot(designer, request.getId(), panorama.getId(), create));

        assertSame(ErrorCode.DESIGN_MEDIA_ASSET_NOT_ALLOWED, exception.getErrorCode());
        assertTrue(panorama.getHotspots().isEmpty());
        verify(boothDesignService, never()).getMediaAssetForCompany(mediaAssetId, request.getCompany().getId(), null);
    }

    @Test
    void createInfoImageHotspotRejectsStagedVideoOrForeignDraftMedia() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedVideo = stagedMedia("video/mp4");
        draft.getMediaAssets().add(stagedVideo);
        UpsertDesignDraftHotspotRequest wrongType = hotspotRequest(HotspotType.INFO);
        wrongType.setInfoContentType(HotspotInfoContentType.IMAGE);
        wrongType.setDesignDraftMediaAssetId(stagedVideo.getId());

        assertSame(
                ErrorCode.INVALID_DESIGN_DRAFT,
                assertThrows(
                        AppException.class,
                        () -> service.createHotspot(
                                designer,
                                request.getId(),
                                panorama.getId(),
                                wrongType))
                        .getErrorCode());

        UpsertDesignDraftHotspotRequest foreignMedia = hotspotRequest(HotspotType.INFO);
        foreignMedia.setInfoContentType(HotspotInfoContentType.IMAGE);
        foreignMedia.setDesignDraftMediaAssetId(UUID.randomUUID());
        assertSame(
                ErrorCode.INVALID_DESIGN_DRAFT,
                assertThrows(
                        AppException.class,
                        () -> service.createHotspot(
                                designer,
                                request.getId(),
                                panorama.getId(),
                                foreignMedia))
                        .getErrorCode());
    }

    @Test
    void updateHotspotReplacingStagedMediaKeepsBothMediaAvailable() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset originalMedia = stagedMedia("image/png");
        DesignDraftMediaAsset replacementMedia = stagedMedia("image/jpeg");
        draft.getMediaAssets().addAll(List.of(originalMedia, replacementMedia));
        DesignDraftHotspot hotspot = stagedMediaHotspot(panorama, originalMedia);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);
        UpsertDesignDraftHotspotRequest update = hotspotRequest(HotspotType.MEDIA);
        update.setDesignDraftMediaAssetId(replacementMedia.getId());

        service.updateHotspot(designer, request.getId(), panorama.getId(), hotspot.getId(), update);

        assertSame(replacementMedia, hotspot.getDesignDraftMediaAsset());
        assertEquals(List.of(originalMedia, replacementMedia), draft.getMediaAssets());
        verify(assetService, never()).cleanupUnreferencedAssets(request);
    }

    @Test
    void updateHotspotToTextKeepsDetachedStagedMediaAvailable() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedMedia = stagedMedia("image/png");
        draft.getMediaAssets().add(stagedMedia);
        DesignDraftHotspot hotspot = stagedMediaHotspot(panorama, stagedMedia);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);
        UpsertDesignDraftHotspotRequest update = hotspotRequest(HotspotType.INFO);
        update.setInfoContentType(HotspotInfoContentType.TEXT);
        update.setInfoText("Updated text");

        service.updateHotspot(designer, request.getId(), panorama.getId(), hotspot.getId(), update);

        assertNull(hotspot.getDesignDraftMediaAsset());
        assertEquals(List.of(stagedMedia), draft.getMediaAssets());
        verify(assetService, never()).cleanupUnreferencedAssets(request);
    }

    @Test
    void deleteHotspotKeepsStagedMediaAvailableForReuse() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedMedia = stagedMedia("image/png");
        draft.getMediaAssets().add(stagedMedia);
        DesignDraftHotspot hotspot = stagedMediaHotspot(panorama, stagedMedia);
        when(benefitGuardService.calculateUsage(draft)).thenReturn(emptyUsage);

        service.deleteHotspot(designer, request.getId(), panorama.getId(), hotspot.getId());

        assertTrue(panorama.getHotspots().isEmpty());
        assertEquals(List.of(stagedMedia), draft.getMediaAssets());

        UpsertDesignDraftHotspotRequest create = hotspotRequest(HotspotType.MEDIA);
        create.setDesignDraftMediaAssetId(stagedMedia.getId());
        service.createHotspot(designer, request.getId(), panorama.getId(), create);

        assertSame(stagedMedia, panorama.getHotspots().get(0).getDesignDraftMediaAsset());
        verify(assetService, never()).cleanupUnreferencedAssets(request);
    }

    @Test
    void deletePanoramaWithHotspotKeepsStagedMediaAvailable() {
        DesignDraftPanorama panorama = panorama("main", 0, true);
        draft.getPanoramas().add(panorama);
        DesignDraftMediaAsset stagedMedia = stagedMedia("image/png");
        draft.getMediaAssets().add(stagedMedia);
        stagedMediaHotspot(panorama, stagedMedia);

        service.deletePanorama(designer, request.getId(), panorama.getId());

        assertTrue(draft.getPanoramas().isEmpty());
        assertEquals(List.of(stagedMedia), draft.getMediaAssets());
        verify(assetService, never()).cleanupUnreferencedAssets(request);
    }

    @Test
    void updateSettingsClearsDescriptionAndRestoresBaselineAssetForKeep() {
        UUID baselineId = UUID.randomUUID();
        request.getBooth().setThumbnailPublicId("booth/thumbnail");
        DesignDraftAsset baseline = DesignDraftAsset.builder()
                .id(baselineId)
                .designRequest(request)
                .publicId("booth/thumbnail")
                .url("https://cdn/thumbnail.jpg")
                .assetType(DesignDraftAssetType.THUMBNAIL)
                .build();
        when(draftAssetRepository.findByDesignRequestIdAndPublicId(request.getId(), "booth/thumbnail"))
                .thenReturn(Optional.of(baseline));
        UpdateDesignDraftSettingsRequest update = new UpdateDesignDraftSettingsRequest(
                "note",
                "New booth",
                null,
                "classic",
                DesignDraftFileAction.KEEP,
                null,
                DesignDraftFileAction.CLEAR,
                null);

        service.updateSettings(designer, request.getId(), update);

        assertEquals("New booth", draft.getBoothName());
        assertNull(draft.getBoothDescription());
        assertSame(baseline, draft.getThumbnailAsset());
        assertNull(draft.getBackgroundMusicAsset());
        assertEquals(DesignDraftFileAction.CLEAR, draft.getBackgroundMusicAction());
        assertFalse(draft.getNote().isBlank());
    }

    @Test
    void addMediaAsset_DuplicateAssetId_ThrowsAppException() {
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .build();
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder()
                .draft(draft)
                .asset(asset)
                .title("Existing asset")
                .build());

        SubmitDesignDraftMediaAssetRequest req = new SubmitDesignDraftMediaAssetRequest(null, assetId, "Duplicate", 0);

        AppException ex = assertThrows(AppException.class,
                () -> service.addMediaAsset(designer, request.getId(), req));
        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void addMediaAsset_InvalidAssetType_ThrowsAppException() {
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset panoramaAsset = DesignDraftAsset.builder()
                .id(assetId)
                .assetType(DesignDraftAssetType.PANORAMA)
                .build();

        SubmitDesignDraftMediaAssetRequest req = new SubmitDesignDraftMediaAssetRequest(null, assetId,
                "Media Attachment", 0);
        when(draftAssetRepository.findByIdAndDesignRequestId(assetId, request.getId()))
                .thenReturn(Optional.of(panoramaAsset));

        AppException ex = assertThrows(AppException.class,
                () -> service.addMediaAsset(designer, request.getId(), req));
        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void reorderMediaAssets_ForeignOrNonExistentId_ThrowsAppException() {
        UUID existingId = UUID.randomUUID();
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder().id(existingId).draft(draft).build());

        List<UUID> invalidList = List.of(existingId, UUID.randomUUID());

        AppException ex = assertThrows(AppException.class,
                () -> service.reorderMediaAssets(designer, request.getId(), invalidList));
        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void reorderMediaAssets_DuplicateId_ThrowsAppException() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder().id(firstId).draft(draft).build());
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder().id(secondId).draft(draft).build());

        AppException ex = assertThrows(
                AppException.class,
                () -> service.reorderMediaAssets(designer, request.getId(), List.of(firstId, firstId)));

        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void addMediaAsset_IgnoresClientSortOrderAndAppendsToDraft() {
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .build();
        when(draftAssetRepository.findByIdAndDesignRequestId(assetId, request.getId()))
                .thenReturn(Optional.of(asset));

        service.addMediaAsset(
                designer,
                request.getId(),
                new SubmitDesignDraftMediaAssetRequest(null, assetId, " Intro ", -50));

        assertEquals(0, draft.getMediaAssets().get(0).getSortOrder());
        assertEquals("Intro", draft.getMediaAssets().get(0).getTitle());
    }

    @Test
    void addMediaAsset_AppendsAfterHighestServerOrderWhenDraftHasGap() {
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(DesignDraftAsset.builder().id(UUID.randomUUID()).build())
                .sortOrder(0)
                .build());
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(DesignDraftAsset.builder().id(UUID.randomUUID()).build())
                .sortOrder(2)
                .build());
        UUID newAssetId = UUID.randomUUID();
        when(draftAssetRepository.findByIdAndDesignRequestId(newAssetId, request.getId()))
                .thenReturn(Optional.of(DesignDraftAsset.builder()
                        .id(newAssetId)
                        .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                        .build()));

        service.addMediaAsset(
                designer,
                request.getId(),
                new SubmitDesignDraftMediaAssetRequest(null, newAssetId, "New", -1));

        assertEquals(3, draft.getMediaAssets().get(2).getSortOrder());
    }

    @Test
    void addMediaAsset_TitleLongerThanColumn_ThrowsAppException() {
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .build();
        when(draftAssetRepository.findByIdAndDesignRequestId(assetId, request.getId()))
                .thenReturn(Optional.of(asset));

        AppException ex = assertThrows(
                AppException.class,
                () -> service.addMediaAsset(
                        designer,
                        request.getId(),
                        new SubmitDesignDraftMediaAssetRequest(null, assetId, "a".repeat(256), null)));

        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void removeMediaAsset_NonExistentId_ThrowsAppException() {
        AppException ex = assertThrows(AppException.class,
                () -> service.removeMediaAsset(designer, request.getId(), UUID.randomUUID()));
        assertEquals(ErrorCode.INVALID_DESIGN_DRAFT, ex.getErrorCode());
    }

    @Test
    void removeMediaAsset_KeepsStagingAssetAvailableForReuse() {
        UUID mediaId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(assetId)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .build();
        draft.getMediaAssets().add(DesignDraftMediaAsset.builder()
                .id(mediaId)
                .draft(draft)
                .asset(asset)
                .build());

        service.removeMediaAsset(designer, request.getId(), mediaId);

        assertTrue(draft.getMediaAssets().isEmpty());
        verify(assetService, never()).cleanupUnreferencedAssets(request);

        when(draftAssetRepository.findByIdAndDesignRequestId(assetId, request.getId()))
                .thenReturn(Optional.of(asset));
        service.addMediaAsset(
                designer,
                request.getId(),
                new SubmitDesignDraftMediaAssetRequest(null, assetId, "Reusable", null));

        assertEquals(1, draft.getMediaAssets().size());
        assertSame(asset, draft.getMediaAssets().get(0).getAsset());
    }

    @Test
    void removeMediaAsset_WhenReferencedByHotspot_KeepsDraftUnchanged() {
        UUID mediaId = UUID.randomUUID();
        DesignDraftMediaAsset media = DesignDraftMediaAsset.builder()
                .id(mediaId)
                .draft(draft)
                .build();
        draft.getMediaAssets().add(media);
        DesignDraftPanorama panorama = DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .build();
        panorama.getHotspots().add(DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .designDraftMediaAsset(media)
                .build());
        draft.getPanoramas().add(panorama);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.removeMediaAsset(designer, request.getId(), mediaId));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
        assertEquals(List.of(media), draft.getMediaAssets());
        verify(draftRepository, never()).saveAndFlush(draft);
        verify(assetService, never()).cleanupUnreferencedAssets(request);
    }

    private DesignDraftMediaAsset stagedMedia(String mimeType) {
        DesignDraftAsset asset = DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .assetType(DesignDraftAssetType.MEDIA_ATTACHMENT)
                .mimeType(mimeType)
                .fileName("attachment")
                .build();
        return DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(asset)
                .title("Attachment")
                .build();
    }

    private DesignDraftHotspot stagedMediaHotspot(
            DesignDraftPanorama panorama,
            DesignDraftMediaAsset stagedMedia) {
        DesignDraftHotspot hotspot = DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .type(HotspotType.MEDIA)
                .name("Media hotspot")
                .designDraftMediaAsset(stagedMedia)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
        panorama.getHotspots().add(hotspot);
        return hotspot;
    }

    private UpsertDesignDraftHotspotRequest hotspotRequest(HotspotType type) {
        UpsertDesignDraftHotspotRequest request = new UpsertDesignDraftHotspotRequest();
        request.setType(type);
        request.setName("Hotspot");
        request.setXPosition(1.0);
        request.setYPosition(2.0);
        request.setZPosition(3.0);
        return request;
    }

    private DesignDraftPanorama panorama(String key, int order, boolean isDefault) {

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
