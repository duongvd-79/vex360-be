package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftStorageMetricsResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetType;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.designrequest.services.DesignDraftStorageMetricsService;

@ExtendWith(MockitoExtension.class)
class DesignDraftStorageMetricsServiceUnitTest {
    @Mock
    private DesignDraftAssetRepository assetRepository;

    @Mock
    private CompanyStorageService storageService;

    @Test
    void calculatesDistinctReferencedAssetsAndIgnoresExemptOrUnusedFiles() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        DesignRequest request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .build();
        DesignDraft draft = DesignDraft.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .versionNumber(0)
                .build();

        DesignDraftAsset baselinePanorama = asset(
                request,
                "panorama/baseline",
                100L,
                DesignDraftAssetType.PANORAMA,
                DesignDraftAssetSource.BOOTH_BASELINE);
        DesignDraftAsset uploadedPanorama = asset(
                request, "panorama/new", 50L, DesignDraftAssetType.PANORAMA, DesignDraftAssetSource.UPLOADED);
        DesignDraftAsset stagedMedia = asset(
                request, "design-media/used", 30L, DesignDraftAssetType.MEDIA_ATTACHMENT, DesignDraftAssetSource.UPLOADED);
        DesignDraftAsset unusedMedia = asset(
                request, "design-media/unused", 70L, DesignDraftAssetType.MEDIA_ATTACHMENT, DesignDraftAssetSource.UPLOADED);
        DesignDraftAsset thumbnail = asset(
                request, "thumbnail/free", 90L, DesignDraftAssetType.THUMBNAIL, DesignDraftAssetSource.UPLOADED);
        DesignDraftMediaAsset draftMedia = DesignDraftMediaAsset.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .asset(stagedMedia)
                .build();
        draft.getMediaAssets().addAll(List.of(
                draftMedia,
                DesignDraftMediaAsset.builder()
                        .id(UUID.randomUUID())
                        .draft(draft)
                        .asset(unusedMedia)
                        .build()));

        MediaAsset officialMedia = MediaAsset.builder()
                .id(UUID.randomUUID())
                .publicId("official/media")
                .fileSize(40L)
                .build();
        DesignDraftPanorama baseline = panorama(draft, "panorama/baseline");
        baseline.getHotspots().add(hotspot(baseline, draftMedia, null));
        baseline.getHotspots().add(hotspot(baseline, draftMedia, null));
        baseline.getHotspots().add(hotspot(baseline, null, officialMedia));
        draft.getPanoramas().addAll(List.of(baseline, panorama(draft, "panorama/new")));

        when(assetRepository.findByDesignRequestId(request.getId()))
                .thenReturn(List.of(baselinePanorama, uploadedPanorama, stagedMedia, unusedMedia, thumbnail));
        when(storageService.getUsage(company)).thenReturn(StorageUsageResponseDTO.builder()
                .availableBytes(500L)
                .build());

        DesignDraftStorageMetricsResponseDTO result =
                new DesignDraftStorageMetricsService(assetRepository, storageService).calculate(draft);

        assertEquals(220L, result.getProjectedTotalStorageBytes());
        assertEquals(80L, result.getProjectedNewAssetsStorageBytes());
        assertEquals(500L, result.getExhibitorAvailableStorageBytes());
    }

    private DesignDraftAsset asset(
            DesignRequest request,
            String publicId,
            long fileSize,
            DesignDraftAssetType type,
            DesignDraftAssetSource source) {
        return DesignDraftAsset.builder()
                .id(UUID.randomUUID())
                .designRequest(request)
                .publicId(publicId)
                .fileSize(fileSize)
                .assetType(type)
                .assetSource(source)
                .build();
    }

    private DesignDraftPanorama panorama(DesignDraft draft, String imageKey) {
        return DesignDraftPanorama.builder()
                .id(UUID.randomUUID())
                .draft(draft)
                .imageKey(imageKey)
                .build();
    }

    private DesignDraftHotspot hotspot(
            DesignDraftPanorama panorama,
            DesignDraftMediaAsset draftMedia,
            MediaAsset officialMedia) {
        return DesignDraftHotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .designDraftMediaAsset(draftMedia)
                .mediaAsset(officialMedia)
                .build();
    }
}
