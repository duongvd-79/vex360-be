package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.services.DesignRequestMediaAssetService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestMediaAssetServiceUnitTest {
    @Mock
    DesignRequestMediaAssetRepository requestMediaAssetRepository;
    @Mock
    MediaAssetRepository mediaAssetRepository;
    @Mock
    HotspotRepository hotspotRepository;
    @Mock
    ExhibitorMediaAssetService exhibitorMediaAssetService;
    @Mock
    BoothDesignService boothDesignService;

    private DesignRequestMediaAssetService service;
    private Company company;
    private DesignRequest request;

    @BeforeEach
    void setup() {
        service = new DesignRequestMediaAssetService(
                requestMediaAssetRepository, exhibitorMediaAssetService, boothDesignService);
        company = Company.builder().id(UUID.randomUUID()).build();
        request = DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).company(company).build())
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
    }

    @Test
    void initialDesignAddsSelectedAssetsAsOptionalAndDeduplicatesIds() {
        MediaAsset selected = mediaAsset(company);
        when(exhibitorMediaAssetService.findMediaAssetsByIds(any()))
                .thenReturn(List.of(selected));

        service.initializeAllowlist(request, List.of(selected.getId(), selected.getId()));

        assertEquals(1, request.getMediaAssets().size());
        DesignRequestMediaAsset item = request.getMediaAssets().get(0);
        assertSame(selected, item.getMediaAsset());
        assertFalse(item.getRequiredFromBaseline());
    }

    @Test
    void redesignAddsBoothAssetsAsRequiredAndKeepsExtraSelectionOptional() {
        request.setMode(DesignRequestMode.REDESIGN);
        MediaAsset selected = mediaAsset(company);
        MediaAsset baseline = mediaAsset(company);
        when(exhibitorMediaAssetService.findMediaAssetsByIds(List.of(selected.getId())))
                .thenReturn(List.of(selected));
        when(exhibitorMediaAssetService.findMediaAssetsByIds(List.of(baseline.getId())))
                .thenReturn(List.of(baseline));
        when(boothDesignService.findDistinctMediaAssetIdsByBoothId(
                request.getBooth().getId(), null)).thenReturn(List.of(baseline.getId()));

        service.initializeAllowlist(request, List.of(selected.getId()));

        assertEquals(2, request.getMediaAssets().size());
        assertTrue(request.getMediaAssets().stream()
                .anyMatch(item -> item.getMediaAsset() == baseline && item.getRequiredFromBaseline()));
        assertTrue(request.getMediaAssets().stream()
                .anyMatch(item -> item.getMediaAsset() == selected && !item.getRequiredFromBaseline()));
    }

    @Test
    void selectedAssetOutsideCompanyIsRejected() {
        UUID mediaAssetId = UUID.randomUUID();
        Mockito.lenient().when(mediaAssetRepository.findByIdInAndCompanyId(List.of(mediaAssetId), company.getId()))
                .thenReturn(List.of());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(request, List.of(mediaAssetId)));

        assertSame(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void nullSelectedAssetIdIsRejected() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(request, java.util.Arrays.asList((UUID) null)));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    @Test
    void officialAssetOutsideAllowlistIsRejected() {
        UUID mediaAssetId = UUID.randomUUID();
        when(requestMediaAssetRepository.existsByDesignRequestIdAndMediaAssetId(request.getId(), mediaAssetId))
                .thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.assertMediaAssetAllowed(request, mediaAssetId));

        assertSame(ErrorCode.DESIGN_MEDIA_ASSET_NOT_ALLOWED, exception.getErrorCode());
    }

    private MediaAsset mediaAsset(Company owner) {
        return MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(owner)
                .build();
    }
}
