package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotProductSummaryDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.repositories.ProductPlacementProjection;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.booth.services.impl.VisitorBoothServiceImpl;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.product.dtos.response.ProductContentResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.dtos.response.VisitorProductSearchResponseDTO;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class VisitorBoothServiceUnitTest {

    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private HotspotRepository hotspotRepository;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private ProductService productService;
    @Mock
    private BoothMapper boothMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductPlacementProjection firstPlacement;
    @Mock
    private ProductPlacementProjection secondPlacement;

    private VisitorBoothService service;

    private UUID exhibitionUuid;
    private ExhibitionResponseDTO exhibition;
    private Pageable pageable;

    @BeforeEach
    void setup() {
        service = new VisitorBoothServiceImpl(
                exhibitionService,
                boothRepository,
                hotspotRepository,
                panoramaRepository,
                productService,
                boothMapper,
                productMapper);
        exhibitionUuid = UUID.randomUUID();
        exhibition = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .status(ExhibitionStatus.ACTIVE.name())
                .build();
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getPublishedBooths_WhenExhibitionIsActive_ReturnsBooths() {
        String keyword = "test";
        Booth booth = Booth.builder().id(UUID.randomUUID()).name("Test Booth").build();
        Page<Booth> boothPage = new PageImpl<>(List.of(booth));
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        responseDTO.setName("Test Booth");

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothsByExhibitionUuid(
                exhibitionUuid,
                BoothStatus.PUBLISHED,
                keyword,
                BoothListingPriority.FEATURED,
                pageable))
                .thenReturn(boothPage);
        when(boothMapper.toBoothResponseDTO(booth)).thenReturn(responseDTO);

        PageResponse<BoothResponseDTO> result = service.getPublishedBooths(
                exhibitionUuid,
                keyword,
                BoothListingPriority.FEATURED,
                pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Booth", result.getContent().get(0).getName());
    }

    @Test
    void getPublishedBooths_WhenExhibitionIsPublished_ReturnsBooths() {
        exhibition.setStatus(ExhibitionStatus.PUBLISHED.name());
        Booth booth = Booth.builder().id(UUID.randomUUID()).name("Published Booth").build();
        Page<Booth> boothPage = new PageImpl<>(List.of(booth));
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        responseDTO.setName("Published Booth");

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothsByExhibitionUuid(
                exhibitionUuid, BoothStatus.PUBLISHED, null, null, pageable))
                .thenReturn(boothPage);
        when(boothMapper.toBoothResponseDTO(booth)).thenReturn(responseDTO);

        PageResponse<BoothResponseDTO> result = service.getPublishedBooths(
                exhibitionUuid, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void getPublishedBooths_WhenExhibitionNotActive_ThrowsException() {
        exhibition.setStatus(ExhibitionStatus.REGISTRATION.name());
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        AppException exception = assertThrows(AppException.class,
                () -> service.getPublishedBooths(exhibitionUuid, "test", null, pageable));

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, exception.getErrorCode());
    }

    @Test
    void getPublishedBooths_WhenExhibitionIsCompleted_ThrowsException() {
        exhibition.setStatus(ExhibitionStatus.COMPLETED.name());
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        AppException ex = assertThrows(AppException.class,
                () -> service.getPublishedBooths(exhibitionUuid, null, null, pageable));

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    void getPublishedBooths_WhenExhibitionIsPending_ThrowsException() {
        exhibition.setStatus(ExhibitionStatus.PENDING.name());
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        AppException ex = assertThrows(AppException.class,
                () -> service.getPublishedBooths(exhibitionUuid, null, null, pageable));

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    void getPublishedBooths_WhenExhibitionNotFound_ThrowsException() {
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid))
                .thenThrow(new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        AppException exception = assertThrows(AppException.class,
                () -> service.getPublishedBooths(exhibitionUuid, "test", null, pageable));

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void searchDisplayedProducts_WhenProductHasMultiplePlacements_ReturnsOneProductWithAllPlacements() {
        UUID productId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID firstHotspotId = UUID.randomUUID();
        UUID secondHotspotId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .company(Company.builder().id(companyId).name("Test Company").build())
                .category(ProductCategory.builder().id(categoryId).name("Robotics").build())
                .name("Robot X1")
                .sku("RX-001")
                .status(ProductStatus.ACTIVE)
                .build();
        Page<Product> productPage = new PageImpl<>(List.of(product), pageable, 1);

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(hotspotRepository.searchDisplayedProductsForVisitor(
                exhibitionUuid,
                "robot",
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED,
                pageable)).thenReturn(productPage);
        mockPlacement(firstPlacement, productId, firstHotspotId, BoothListingPriority.FEATURED);
        mockPlacement(secondPlacement, productId, secondHotspotId, null);
        when(secondPlacement.getPackageListingPriority()).thenReturn(BoothListingPriority.PRIORITY);
        when(hotspotRepository.findProductPlacements(
                exhibitionUuid,
                List.of(productId),
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED)).thenReturn(List.of(firstPlacement, secondPlacement));

        PageResponse<VisitorProductSearchResponseDTO> result = service.searchDisplayedProducts(
                exhibitionUuid,
                " robot ",
                pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(productId, result.getContent().get(0).getId());
        assertEquals(2, result.getContent().get(0).getPlacements().size());
        assertEquals(BoothListingPriority.FEATURED,
                result.getContent().get(0).getPlacements().get(0).getListingPriority());
        assertEquals(BoothListingPriority.PRIORITY,
                result.getContent().get(0).getPlacements().get(1).getListingPriority());
    }

    @Test
    void searchDisplayedProducts_WhenPageIsEmpty_DoesNotLoadPlacements() {
        Page<Product> emptyPage = Page.empty(pageable);
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(hotspotRepository.searchDisplayedProductsForVisitor(
                exhibitionUuid,
                null,
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED,
                pageable)).thenReturn(emptyPage);

        PageResponse<VisitorProductSearchResponseDTO> result = service.searchDisplayedProducts(
                exhibitionUuid,
                "  ",
                pageable);

        assertEquals(0, result.getTotalElements());
        verify(hotspotRepository, never()).findProductPlacements(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void getDisplayedProductDetail_WhenDisplayed_ReturnsFullProduct() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).status(ProductStatus.ACTIVE).build();
        ProductResponseDTO response = new ProductResponseDTO();
        response.setId(productId);

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(hotspotRepository.findDisplayedProductDetailForVisitor(
                exhibitionUuid,
                productId,
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponseDTO result = service.getDisplayedProductDetail(exhibitionUuid, productId);

        assertEquals(response, result);
    }

    @Test
    void getDisplayedProductDetail_WhenNotDisplayed_ThrowsProductNotFound() {
        UUID productId = UUID.randomUUID();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(hotspotRepository.findDisplayedProductDetailForVisitor(
                exhibitionUuid,
                productId,
                ProductStatus.ACTIVE,
                BoothStatus.PUBLISHED)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.getDisplayedProductDetail(exhibitionUuid, productId));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getBoothTourDetail_WhenExhibitionIsActiveAndBoothExists_ReturnsBoothDetail() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Test Booth").build();
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        responseDTO.setName("Test Booth");

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibitionUuid, boothId, BoothStatus.PUBLISHED))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.findDetailsByBoothId(boothId)).thenReturn(List.of());
        when(boothMapper.toBoothResponseDTO(booth, List.of())).thenReturn(responseDTO);

        BoothResponseDTO result = service.getBoothTourDetail(exhibitionUuid, boothId);

        assertNotNull(result);
        assertEquals("Test Booth", result.getName());
        verify(productService, never()).findActiveProductResponsesByIds(any());
    }

    @Test
    void getBoothTourDetail_EnrichesActiveProductContents() {
        UUID boothId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Test Booth").build();
        BoothResponseDTO responseDTO = boothResponseWithProduct(productId);
        ProductContentResponseDTO content = new ProductContentResponseDTO(
                UUID.randomUUID(),
                "https://example.com/product.jpg",
                ProductContentType.IMAGE,
                0,
                "image/jpeg",
                100L);
        ProductResponseDTO productDetail = new ProductResponseDTO();
        productDetail.setId(productId);
        productDetail.setDescription("Full description");
        productDetail.setContents(List.of(content));

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibitionUuid, boothId, BoothStatus.PUBLISHED)).thenReturn(Optional.of(booth));
        when(panoramaRepository.findDetailsByBoothId(boothId)).thenReturn(List.of());
        when(boothMapper.toBoothResponseDTO(booth, List.of())).thenReturn(responseDTO);
        when(productService.findActiveProductResponsesByIds(List.of(productId)))
                .thenReturn(Map.of(productId, productDetail));

        BoothResponseDTO result = service.getBoothTourDetail(exhibitionUuid, boothId);

        HotspotProductSummaryDTO productSummary = result.getPanoramas().get(0).getHotspots().get(0).getProduct();
        assertEquals("Full description", productSummary.getDescription());
        assertEquals(List.of(content), productSummary.getContents());
    }

    @Test
    void getBoothTourDetail_RemovesInactiveProductFromHotspot() {
        UUID boothId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Test Booth").build();
        BoothResponseDTO responseDTO = boothResponseWithProduct(productId);

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibitionUuid, boothId, BoothStatus.PUBLISHED)).thenReturn(Optional.of(booth));
        when(panoramaRepository.findDetailsByBoothId(boothId)).thenReturn(List.of());
        when(boothMapper.toBoothResponseDTO(booth, List.of())).thenReturn(responseDTO);
        when(productService.findActiveProductResponsesByIds(List.of(productId))).thenReturn(Map.of());

        BoothResponseDTO result = service.getBoothTourDetail(exhibitionUuid, boothId);

        assertNull(result.getPanoramas().get(0).getHotspots().get(0).getProduct());
    }

    @Test
    void getBoothTourDetail_WhenBoothNotFound_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                eq(exhibitionUuid), eq(boothId), eq(BoothStatus.PUBLISHED)))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.getBoothTourDetail(exhibitionUuid, boothId));

        assertEquals(ErrorCode.BOOTH_NOT_FOUND, exception.getErrorCode());
    }

    private void mockPlacement(
            ProductPlacementProjection placement,
            UUID productId,
            UUID hotspotId,
            BoothListingPriority priority) {
        when(placement.getProductId()).thenReturn(productId);
        when(placement.getHotspotId()).thenReturn(hotspotId);
        when(placement.getBoothId()).thenReturn(UUID.randomUUID());
        when(placement.getBoothName()).thenReturn("Test Booth");
        when(placement.getPanoramaId()).thenReturn(UUID.randomUUID());
        when(placement.getPanoramaName()).thenReturn("Main");
        when(placement.getListingPrioritySnapshot()).thenReturn(priority);
    }

    private BoothResponseDTO boothResponseWithProduct(UUID productId) {
        HotspotProductSummaryDTO product = new HotspotProductSummaryDTO();
        product.setId(productId);
        product.setStatus(ProductStatus.ACTIVE);
        HotspotResponseDTO hotspot = new HotspotResponseDTO();
        hotspot.setProduct(product);
        PanoramaResponseDTO panorama = new PanoramaResponseDTO();
        panorama.setHotspots(List.of(hotspot));
        BoothResponseDTO response = new BoothResponseDTO();
        response.setPanoramas(List.of(panorama));
        return response;
    }
}
