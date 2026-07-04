package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothBenefitGuardService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

@ExtendWith(MockitoExtension.class)
class BoothBenefitGuardServiceUnitTest {
    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    private BoothBenefitGuardService boothBenefitGuardService;
    private Booth booth;

    @BeforeEach
    void setup() {
        boothBenefitGuardService = new BoothBenefitGuardService(panoramaRepository, hotspotRepository);
        User exhibitor = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        Company company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitor).name("VEX Company").build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .createdBy(exhibitor)
                .exhibitorRegistration(registration())
                .build();
    }

    @Test
    void assertCanAddPanorama_WhenUnderQuota_Passes() {
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        assertDoesNotThrow(() -> boothBenefitGuardService.assertCanAddPanorama(booth));
    }

    @Test
    void assertCanAddPanorama_WhenQuotaExceeded_ThrowsQuotaExceeded() {
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(3L);

        AppException exception = assertThrows(AppException.class,
                () -> boothBenefitGuardService.assertCanAddPanorama(booth));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void assertCanCreateHotspot_WhenProjectedUsageWithinQuota_Passes() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        MediaAsset video = mediaAsset(MediaAssetType.VIDEO, 512L * 1024L);
        Hotspot candidate = Hotspot.builder().product(product).mediaAsset(video).build();

        when(hotspotRepository.countBySourcePanoramaBoothId(booth.getId())).thenReturn(0L);
        when(hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), null))
                .thenReturn(List.of());
        when(hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(booth.getId(), null))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> boothBenefitGuardService.assertCanCreateHotspot(booth, candidate));
    }

    @Test
    void assertCanCreateHotspot_WhenHotspotQuotaExceeded_ThrowsQuotaExceeded() {
        Hotspot candidate = Hotspot.builder().build();
        when(hotspotRepository.countBySourcePanoramaBoothId(booth.getId())).thenReturn(5L);

        AppException exception = assertThrows(AppException.class,
                () -> boothBenefitGuardService.assertCanCreateHotspot(booth, candidate));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void assertCanCreateHotspot_WhenProductQuotaExceeded_ThrowsQuotaExceeded() {
        Product product = Product.builder().id(UUID.randomUUID()).build();
        Hotspot candidate = Hotspot.builder().product(product).build();

        when(hotspotRepository.countBySourcePanoramaBoothId(booth.getId())).thenReturn(0L);
        when(hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), null))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        AppException exception = assertThrows(AppException.class,
                () -> boothBenefitGuardService.assertCanCreateHotspot(booth, candidate));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void assertCanUpdateHotspot_WhenSameProjectedProductCountWithinQuota_Passes() {
        UUID hotspotId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        Hotspot candidate = Hotspot.builder().id(hotspotId).product(product).build();

        when(hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), hotspotId))
                .thenReturn(List.of());
        when(hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(booth.getId(), hotspotId))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> boothBenefitGuardService.assertCanUpdateHotspot(booth, candidate));
    }

    @Test
    void assertCanUpdateHotspot_WhenStorageQuotaExceeded_ThrowsQuotaExceeded() {
        UUID hotspotId = UUID.randomUUID();
        Hotspot candidate = Hotspot.builder()
                .id(hotspotId)
                .mediaAsset(mediaAsset(MediaAssetType.VIDEO, 2L * 1024L * 1024L))
                .build();

        when(hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(booth.getId(), hotspotId))
                .thenReturn(List.of());
        when(hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(booth.getId(), hotspotId))
                .thenReturn(List.of());

        AppException exception = assertThrows(AppException.class,
                () -> boothBenefitGuardService.assertCanUpdateHotspot(booth, candidate));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void assertCanAddPanorama_WhenRegistrationMissing_ThrowsInvalidBooth() {
        booth.setExhibitorRegistration(null);

        AppException exception = assertThrows(AppException.class,
                () -> boothBenefitGuardService.assertCanAddPanorama(booth));

        assertSame(ErrorCode.INVALID_BOOTH, exception.getErrorCode());
    }

    private ExhibitorRegistration registration() {
        return ExhibitorRegistration.builder()
                .status(ExhibitorRegistrationStatus.APPROVED)
                .maxProductsPerBoothSnapshot(2)
                .maxEmbeddedVideosPerBoothSnapshot(1)
                .maxPanoramasPerBoothSnapshot(3)
                .maxHotspotsPerBoothSnapshot(5)
                .storageLimitMbSnapshot(1L)
                .build();
    }

    private MediaAsset mediaAsset(MediaAssetType type, Long fileSize) {
        return MediaAsset.builder()
                .id(UUID.randomUUID())
                .type(type)
                .fileSize(fileSize)
                .build();
    }
}
