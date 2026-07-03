package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
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
