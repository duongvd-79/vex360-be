package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ExhibitorHotspotServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private ProductService productService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private CompanyService companyService;

    private ExhibitorHotspotService exhibitorHotspotService;
    private User exhibitorUser;
    private Company company;
    private Booth booth;
    private Panorama panorama;

    @BeforeEach
    void setup() {
        exhibitorHotspotService = new ExhibitorHotspotService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                productService,
                mediaAssetRepository,
                companyService,
                Mappers.getMapper(BoothMapper.class));
        exhibitorUser = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
                .build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(exhibitorUser)
                .name("VEX Company")
                .build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .createdBy(exhibitorUser)
                .build();
        panorama = Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name("Entrance")
                .imageUrl("/uploads/panoramas/entrance.jpg")
                .imageKey("entrance.jpg")
                .orderIndex(0)
                .isDefault(true)
                .build();
    }

    @Test
    void createProductHotspotSucceedsWithActiveProductAndReturnsThumbnail() {
        Product product = product(ProductStatus.ACTIVE);
        mockBoothAndPanorama();
        when(productService.getProductForCompany(product.getId(), company)).thenReturn(product);
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(invocation -> {
            Hotspot hotspot = invocation.getArgument(0);
            hotspot.setId(UUID.randomUUID());
            return hotspot;
        });

        HotspotResponseDTO response = exhibitorHotspotService.createHotspot(
                exhibitorUser,
                booth.getId(),
                panorama.getId(),
                productHotspotRequest(product.getId()));

        assertEquals(HotspotType.PRODUCT, response.getType());
        assertEquals(product.getId(), response.getProduct().getId());
        assertEquals("https://cdn.example/product.png", response.getProduct().getThumbnailUrl());
        assertEquals(ProductStatus.ACTIVE, response.getProduct().getStatus());
    }

    @Test
    void createProductHotspotRejectsInactiveProduct() {
        Product product = product(ProductStatus.INACTIVE);
        mockBoothAndPanorama();
        when(productService.getProductForCompany(product.getId(), company)).thenReturn(product);

        AppException exception = assertThrows(AppException.class, () -> exhibitorHotspotService.createHotspot(
                exhibitorUser,
                booth.getId(),
                panorama.getId(),
                productHotspotRequest(product.getId())));

        assertSame(ErrorCode.INVALID_PRODUCT_STATUS, exception.getErrorCode());
    }

    private void mockBoothAndPanorama() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothId(panorama.getId(), booth.getId()))
                .thenReturn(Optional.of(panorama));
    }

    @Test
    void getHotspots_Succeeds() {
        mockBoothAndPanorama();
        Hotspot hotspot = Hotspot.builder().id(UUID.randomUUID()).name("Hotspot A").sourcePanorama(panorama).build();
        when(hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(panorama.getId())).thenReturn(List.of(hotspot));

        List<HotspotResponseDTO> response = exhibitorHotspotService.getHotspots(exhibitorUser, booth.getId(),
                panorama.getId());

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Hotspot A", response.get(0).getName());
    }

    @Test
    void createHotspot_NavigationType_Succeeds() {
        mockBoothAndPanorama();
        Panorama target = Panorama.builder().id(UUID.randomUUID()).booth(booth).name("Main Room").build();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.NAV, "Go Main", 0.0, 0.0, 0.0,
                target.getId(), null, null, null, "default", 1.0, 1);

        when(panoramaRepository.findByIdAndBoothId(target.getId(), booth.getId())).thenReturn(Optional.of(target));
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> {
            Hotspot h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        HotspotResponseDTO response = exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(),
                panorama.getId(), request);

        assertNotNull(response);
        assertEquals(HotspotType.NAV, response.getType());
        assertEquals("Go Main", response.getName());
        assertEquals(target.getId(), response.getTargetPanoramaId());
    }

    @Test
    void createHotspot_InfoType_Succeeds() {
        mockBoothAndPanorama();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.INFO, null, 0.0, 0.0, 0.0,
                null, null, null, "Welcome Info", "default", 1.0, 1);

        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> {
            Hotspot h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        HotspotResponseDTO response = exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(),
                panorama.getId(), request);

        assertNotNull(response);
        assertEquals(HotspotType.INFO, response.getType());
        assertEquals("Info", response.getName());
        assertEquals("Welcome Info", response.getInfoText());
    }

    @Test
    void createHotspot_MediaType_Succeeds() {
        mockBoothAndPanorama();
        UUID mediaId = UUID.randomUUID();
        MediaAsset media = MediaAsset.builder().id(mediaId).company(company).name("Video Intro").build();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.MEDIA, "Watch this", 0.0, 0.0, 0.0,
                null, null, mediaId, null, "default", 1.0, 1);

        when(mediaAssetRepository.findByIdAndCompanyId(mediaId, company.getId())).thenReturn(Optional.of(media));
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> {
            Hotspot h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        HotspotResponseDTO response = exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(),
                panorama.getId(), request);

        assertNotNull(response);
        assertEquals(HotspotType.MEDIA, response.getType());
        assertEquals("Watch this", response.getName());
        assertEquals(mediaId, response.getMediaAsset().getId());
    }

    @Test
    void createHotspot_InvalidCoordinates_ThrowsException() {
        mockBoothAndPanorama();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.INFO, null, null, 0.0, 0.0,
                null, null, null, "Welcome Info", "default", 1.0, 1);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(), panorama.getId(), request));
        assertSame(ErrorCode.INVALID_HOTSPOT, ex.getErrorCode());
    }

    @Test
    void createHotspot_NavigationTargetMissing_ThrowsException() {
        mockBoothAndPanorama();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.NAV, "Go Main", 0.0, 0.0, 0.0,
                null, null, null, null, "default", 1.0, 1);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(), panorama.getId(), request));
        assertSame(ErrorCode.INVALID_HOTSPOT, ex.getErrorCode());
    }

    @Test
    void createHotspot_InfoTextMissing_ThrowsException() {
        mockBoothAndPanorama();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.INFO, null, 0.0, 0.0, 0.0,
                null, null, null, "   ", "default", 1.0, 1);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(), panorama.getId(), request));
        assertSame(ErrorCode.INVALID_HOTSPOT, ex.getErrorCode());
    }

    @Test
    void createHotspot_MediaAssetMissing_ThrowsException() {
        mockBoothAndPanorama();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.MEDIA, "Watch this", 0.0, 0.0, 0.0,
                null, null, null, null, "default", 1.0, 1);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorHotspotService.createHotspot(exhibitorUser, booth.getId(), panorama.getId(), request));
        assertSame(ErrorCode.INVALID_HOTSPOT, ex.getErrorCode());
    }

    @Test
    void updateHotspot_Succeeds() {
        mockBoothAndPanorama();
        UUID hotspotId = UUID.randomUUID();
        Hotspot hotspot = Hotspot.builder().id(hotspotId).sourcePanorama(panorama).type(HotspotType.INFO)
                .infoText("Old Info").build();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.INFO, "New Info Name", 1.0, 2.0, 3.0,
                null, null, null, "New Info", "default", 1.0, 1);

        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, panorama.getId()))
                .thenReturn(Optional.of(hotspot));
        when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> inv.getArgument(0));

        HotspotResponseDTO response = exhibitorHotspotService.updateHotspot(exhibitorUser, booth.getId(),
                panorama.getId(), hotspotId, request);

        assertNotNull(response);
        assertEquals("New Info Name", response.getName());
        assertEquals("New Info", hotspot.getInfoText());
    }

    @Test
    void updateHotspot_NotFound_ThrowsException() {
        mockBoothAndPanorama();
        UUID hotspotId = UUID.randomUUID();
        UpsertHotspotRequest request = new UpsertHotspotRequest(
                HotspotType.INFO, "New Info Name", 1.0, 2.0, 3.0,
                null, null, null, "New Info", "default", 1.0, 1);

        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, panorama.getId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> exhibitorHotspotService.updateHotspot(exhibitorUser,
                booth.getId(), panorama.getId(), hotspotId, request));
        assertSame(ErrorCode.HOTSPOT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteHotspot_Succeeds() {
        mockBoothAndPanorama();
        UUID hotspotId = UUID.randomUUID();
        Hotspot hotspot = Hotspot.builder().id(hotspotId).sourcePanorama(panorama).type(HotspotType.INFO)
                .infoText("Old Info").build();

        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, panorama.getId()))
                .thenReturn(Optional.of(hotspot));

        HotspotResponseDTO response = exhibitorHotspotService.deleteHotspot(exhibitorUser, booth.getId(),
                panorama.getId(), hotspotId);

        assertNotNull(response);
        assertEquals(hotspotId, response.getId());
        verify(hotspotRepository).delete(hotspot);
    }

    @Test
    void deleteHotspot_NotFound_ThrowsException() {
        mockBoothAndPanorama();
        UUID hotspotId = UUID.randomUUID();

        when(hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, panorama.getId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorHotspotService.deleteHotspot(exhibitorUser, booth.getId(), panorama.getId(), hotspotId));
        assertSame(ErrorCode.HOTSPOT_NOT_FOUND, ex.getErrorCode());
    }

    private Product product(ProductStatus status) {
        return Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Active Product")
                .sku("SKU-001")
                .description("Description")
                .price(BigDecimal.valueOf(100000))
                .currency("VND")
                .thumbnailUrl("https://cdn.example/product.png")
                .thumbnailPublicId("product_public_id")
                .status(status)
                .build();
    }

    private UpsertHotspotRequest productHotspotRequest(UUID productId) {
        return new UpsertHotspotRequest(
                HotspotType.PRODUCT,
                "Featured product",
                0.12,
                1.4,
                -2.1,
                null,
                productId,
                null,
                null,
                "default",
                1.0,
                1);
    }
}
