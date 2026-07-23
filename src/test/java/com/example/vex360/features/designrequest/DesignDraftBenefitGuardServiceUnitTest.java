package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftBenefitUsageResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService;
import com.example.vex360.features.designrequest.services.DesignDraftBenefitGuardService.Usage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignDraftBenefitGuardServiceUnitTest {
    @Mock
    private PanoramaRepository panoramaRepository;

    private DesignDraftBenefitGuardService service;
    private Booth booth;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignDraftBenefitGuardService(panoramaRepository);
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .exhibitorRegistration(registration(3, 5, 2, 1))
                .build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
    }

    @Test
    void calculateUsageCountsDistinctProductsAndVideosOnly() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        MediaAsset video = media(MediaAssetType.VIDEO);
        MediaAsset image = media(MediaAssetType.IMAGE);
        DesignDraft draft = draft(2);
        draft.getPanoramas().get(0).getHotspots().addAll(List.of(
                draftHotspot(product, video),
                draftHotspot(product, video),
                draftHotspot(null, image)));

        Usage usage = service.calculateUsage(draft);

        assertEquals(new Usage(2, 3, 1, 1), usage);
    }

    @Test
    void assertMutationAllowedChecksAllFourLimits() {
        assertQuotaExceeded(() -> service.assertMutationAllowed(request, new Usage(3, 0, 0, 0), draft(4)));

        DesignDraft tooManyHotspots = draft(1);
        for (int i = 0; i < 6; i++) {
            tooManyHotspots.getPanoramas().get(0).getHotspots().add(draftHotspot(null, null));
        }
        assertQuotaExceeded(() -> service.assertMutationAllowed(
                request, new Usage(1, 5, 0, 0), tooManyHotspots));

        DesignDraft tooManyProducts = draft(1);
        for (int i = 0; i < 3; i++) {
            tooManyProducts.getPanoramas().get(0).getHotspots().add(
                    draftHotspot(Product.builder().id(UUID.randomUUID()).build(), null));
        }
        assertQuotaExceeded(() -> service.assertMutationAllowed(
                request, new Usage(1, 2, 2, 0), tooManyProducts));

        DesignDraft tooManyVideos = draft(1);
        tooManyVideos.getPanoramas().get(0).getHotspots().addAll(List.of(
                draftHotspot(null, media(MediaAssetType.VIDEO)),
                draftHotspot(null, media(MediaAssetType.VIDEO))));
        assertQuotaExceeded(() -> service.assertMutationAllowed(
                request, new Usage(1, 1, 0, 1), tooManyVideos));
    }

    @Test
    void redesignMutationAllowsNoIncreaseButNotRestoringPreviouslyReducedUsage() {
        request.setMode(DesignRequestMode.REDESIGN);
        booth.setExhibitorRegistration(registration(5, 5, 5, 5));

        assertDoesNotThrow(() -> service.assertMutationAllowed(request, new Usage(8, 0, 0, 0), draft(7)));
        assertDoesNotThrow(() -> service.assertMutationAllowed(request, new Usage(8, 0, 0, 0), draft(8)));
        assertQuotaExceeded(() -> service.assertMutationAllowed(request, new Usage(8, 0, 0, 0), draft(9)));
        assertQuotaExceeded(() -> service.assertMutationAllowed(request, new Usage(5, 0, 0, 0), draft(6)));
    }

    @Test
    void initialDesignNeverGetsOverLimitGrace() {
        booth.setExhibitorRegistration(registration(5, 5, 5, 5));

        assertQuotaExceeded(() -> service.assertMutationAllowed(request, new Usage(8, 0, 0, 0), draft(8)));
    }

    @Test
    void submissionUsesOfficialBaselineGraceForRedesign() {
        request.setMode(DesignRequestMode.REDESIGN);
        booth.setExhibitorRegistration(registration(5, 5, 5, 5));
        when(panoramaRepository.findDetailsByBoothId(booth.getId())).thenReturn(officialPanoramas(8));

        assertDoesNotThrow(() -> service.assertWithinSubmissionLimits(request, draft(8)));
        assertQuotaExceeded(() -> service.assertWithinSubmissionLimits(request, draft(9)));
    }

    @Test
    void storageSnapshotIsNotRequiredOrValidated() {
        ExhibitorRegistration registration = registration(3, 5, 2, 1);
        registration.setStorageLimitMbSnapshot(null);
        booth.setExhibitorRegistration(registration);
        when(panoramaRepository.findDetailsByBoothId(booth.getId())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.assertWithinSubmissionLimits(request, draft(1)));
    }

    @Test
    void usageResponseContainsLimitBaselineWorkingAndRemainingForFourMetrics() {
        when(panoramaRepository.findDetailsByBoothId(booth.getId())).thenReturn(officialPanoramas(1));

        DesignDraftBenefitUsageResponseDTO response = service.getUsageResponse(request, draft(2));

        assertEquals(3, response.getPanoramas().getLimit());
        assertEquals(1, response.getPanoramas().getBaseline());
        assertEquals(2, response.getPanoramas().getWorking());
        assertEquals(1, response.getPanoramas().getRemaining());
        assertEquals(5, response.getHotspots().getLimit());
        assertEquals(2, response.getProducts().getLimit());
        assertEquals(1, response.getEmbeddedVideos().getLimit());
    }

    @Test
    void baselineUsageResponseUsesCurrentBoothAsWorkingUsage() {
        List<Panorama> official = officialPanoramas(2);
        Product product = Product.builder().id(UUID.randomUUID()).build();
        MediaAsset video = media(MediaAssetType.VIDEO);
        official.get(0).getHotspots().add(Hotspot.builder().product(product).mediaAsset(video).build());
        official.get(1).getHotspots().add(Hotspot.builder().product(product).mediaAsset(video).build());
        when(panoramaRepository.findDetailsByBoothId(booth.getId())).thenReturn(official);

        DesignDraftBenefitUsageResponseDTO response = service.getBaselineUsageResponse(request);

        assertEquals(2, response.getPanoramas().getBaseline());
        assertEquals(2, response.getPanoramas().getWorking());
        assertEquals(1, response.getPanoramas().getRemaining());
        assertEquals(2, response.getHotspots().getBaseline());
        assertEquals(1, response.getProducts().getBaseline());
        assertEquals(1, response.getEmbeddedVideos().getBaseline());
    }

    @Test
    void missingOrNegativeLimitIsInvalidBooth() {
        booth.getExhibitorRegistration().setMaxPanoramasPerBoothSnapshot(null);
        assertInvalidBooth(() -> service.assertMutationAllowed(request, Usage.ZERO, draft(0)));

        booth.setExhibitorRegistration(registration(-1, 5, 2, 1));
        assertInvalidBooth(() -> service.assertMutationAllowed(request, Usage.ZERO, draft(0)));
    }

    private ExhibitorRegistration registration(int panoramas, int hotspots, int products, int videos) {
        return ExhibitorRegistration.builder()
                .maxPanoramasPerBoothSnapshot(panoramas)
                .maxHotspotsPerBoothSnapshot(hotspots)
                .maxProductsPerBoothSnapshot(products)
                .maxEmbeddedVideosPerBoothSnapshot(videos)
                .storageLimitMbSnapshot(0L)
                .build();
    }

    private DesignDraft draft(int panoramaCount) {
        DesignDraft draft = DesignDraft.builder().designRequest(request).versionNumber(0).build();
        for (int index = 0; index < panoramaCount; index++) {
            draft.getPanoramas().add(DesignDraftPanorama.builder()
                    .draft(draft)
                    .clientKey("p" + index)
                    .name("Panorama " + index)
                    .imageUrl("https://cdn.example.com/" + index)
                    .imageKey("image-" + index)
                    .orderIndex(index)
                    .isDefault(index == 0)
                    .build());
        }
        return draft;
    }

    private DesignDraftHotspot draftHotspot(Product product, MediaAsset mediaAsset) {
        return DesignDraftHotspot.builder().product(product).mediaAsset(mediaAsset).build();
    }

    private MediaAsset media(MediaAssetType type) {
        return MediaAsset.builder().id(UUID.randomUUID()).type(type).build();
    }

    private List<Panorama> officialPanoramas(int count) {
        List<Panorama> panoramas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            panoramas.add(Panorama.builder().id(UUID.randomUUID()).hotspots(new ArrayList<Hotspot>()).build());
        }
        return panoramas;
    }

    private void assertQuotaExceeded(Runnable operation) {
        AppException exception = assertThrows(AppException.class, operation::run);
        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    private void assertInvalidBooth(Runnable operation) {
        AppException exception = assertThrows(AppException.class, operation::run);
        assertSame(ErrorCode.INVALID_BOOTH, exception.getErrorCode());
    }
}
