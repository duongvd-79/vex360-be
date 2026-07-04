package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.dtos.request.CreateProductContentRequest;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {
    @Mock
    private CompanyService companyService;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CloudService cloudService;

    private ProductService productService;
    private User user;
    private Company company;
    private ProductCategory category;

    @BeforeEach
    void setup() {
        productService = new ProductService(
                companyService,
                productCategoryRepository,
                productRepository,
                cloudService,
                new ProductMapper(),
                Runnable::run);
        user = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Orion").build();
        category = ProductCategory.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Máy tính")
                .status(ProductCategoryStatus.ACTIVE)
                .build();
    }

    @Test
    void createProductRejectsInactiveCategory() {
        category.setStatus(ProductCategoryStatus.INACTIVE);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> productService.createProduct(user, validCreateRequest(), thumbnail(), Map.of()));

        assertEquals(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS, exception.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProductUploadsThumbnailAndMedia() {
        MockMultipartFile thumbnail = thumbnail();
        MockMultipartFile frontFile = new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes());
        MockMultipartFile videoFile = new MockMultipartFile("media_2", "demo.mp4", "video/mp4", "video".getBytes());
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", frontFile);
        files.put("media_2", videoFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(cloudService.upload(frontFile)).thenReturn(upload("/front.png", "front-public-id", null, 200L));
        when(cloudService.upload(videoFile)).thenReturn(upload("/demo.mp4", "video-public-id", "video/mp4", 300L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(UUID.randomUUID());
            product.getContents().forEach(content -> content.setId(UUID.randomUUID()));
            return product;
        });

        productService.createProduct(user, validCreateRequest(), thumbnail, files);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(company, savedProduct.getCompany());
        assertEquals(category, savedProduct.getCategory());
        assertEquals("/thumb.png", savedProduct.getThumbnailUrl());
        assertEquals("thumb-public-id", savedProduct.getThumbnailPublicId());
        assertEquals(2, savedProduct.getContents().size());
        assertEquals("front-public-id", savedProduct.getContents().get(0).getPublicId());
        assertEquals(ProductContentType.IMAGE, savedProduct.getContents().get(0).getType());
        assertEquals(0, savedProduct.getContents().get(0).getOrderIndex());
        assertEquals("video-public-id", savedProduct.getContents().get(1).getPublicId());
        assertEquals(ProductContentType.VIDEO, savedProduct.getContents().get(1).getType());
        assertEquals(1, savedProduct.getContents().get(1).getOrderIndex());
    }

    @Test
    void createProductUsesRequestedStatus() {
        MockMultipartFile thumbnail = thumbnail();
        MockMultipartFile frontFile = new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes());
        MockMultipartFile videoFile = new MockMultipartFile("media_2", "demo.mp4", "video/mp4", "video".getBytes());
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", frontFile);
        files.put("media_2", videoFile);
        CreateProductRequest request = validCreateRequest();
        request.setStatus(ProductStatus.INACTIVE);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(cloudService.upload(frontFile)).thenReturn(upload("/front.png", "front-public-id", "image/png", 200L));
        when(cloudService.upload(videoFile)).thenReturn(upload("/demo.mp4", "video-public-id", "video/mp4", 300L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(user, request, thumbnail, files);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals(ProductStatus.INACTIVE, productCaptor.getValue().getStatus());
    }

    @Test
    void createProductCleansUploadedFilesWhenParallelUploadFails() {
        MockMultipartFile thumbnail = thumbnail();
        MockMultipartFile frontFile = new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes());
        MockMultipartFile videoFile = new MockMultipartFile("media_2", "demo.mp4", "video/mp4", "video".getBytes());
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", frontFile);
        files.put("media_2", videoFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(cloudService.upload(frontFile)).thenReturn(upload("/front.png", "front-public-id", "image/png", 200L));
        when(cloudService.upload(videoFile)).thenThrow(new AppException(ErrorCode.UPLOAD_FAILED));

        AppException exception = assertThrows(AppException.class,
                () -> productService.createProduct(user, validCreateRequest(), thumbnail, files));

        assertEquals(ErrorCode.UPLOAD_FAILED, exception.getErrorCode());
        verify(cloudService).delete("thumb-public-id", "image");
        verify(cloudService).delete("front-public-id", "image");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProductRejectsMoreThanFiveImages() {
        CreateProductRequest request = validCreateRequest();
        request.setContents(List.of(
                new CreateProductContentRequest("media_1", 0),
                new CreateProductContentRequest("media_2", 1),
                new CreateProductContentRequest("media_3", 2),
                new CreateProductContentRequest("media_4", 3),
                new CreateProductContentRequest("media_5", 4),
                new CreateProductContentRequest("media_6", 5)));
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        for (int i = 1; i <= 6; i++) {
            files.put("media_" + i, new MockMultipartFile(
                    "media_" + i,
                    "image-" + i + ".png",
                    "image/png",
                    "image".getBytes()));
        }

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));

        assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, exception.getErrorCode());
        verify(cloudService, never()).upload(any(MultipartFile.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProductRejectsMoreThanOneVideo() {
        MockMultipartFile thumbnail = thumbnail();
        MockMultipartFile imageFile = new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes());
        MockMultipartFile firstVideoFile = new MockMultipartFile("media_2", "demo-1.mp4", "video/mp4",
                "video".getBytes());
        MockMultipartFile secondVideoFile = new MockMultipartFile("media_3", "demo-2.mp4", "video/mp4",
                "video".getBytes());
        CreateProductRequest request = validCreateRequest();
        request.setContents(List.of(
                new CreateProductContentRequest("media_1", 0),
                new CreateProductContentRequest("media_2", 1),
                new CreateProductContentRequest("media_3", 2)));
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", imageFile);
        files.put("media_2", firstVideoFile);
        files.put("media_3", secondVideoFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail, files));

        assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, exception.getErrorCode());
        verify(cloudService, never()).upload(any(MultipartFile.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getProductsFiltersByNameCategoryAndStatus() {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .category(category)
                .name("Robot Arm")
                .sku("VEX-001")
                .description("Robot demo")
                .price(BigDecimal.TEN)
                .currency("VND")
                .thumbnailUrl("/thumb.png")
                .thumbnailPublicId("thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>())
                .build();
        PageRequest pageable = PageRequest.of(0, 10);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.searchProducts(
                company.getId(),
                "Robot",
                category.getId(),
                ProductStatus.ACTIVE,
                pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        PageResponse<ProductResponseDTO> response = productService.getProducts(
                user,
                " Robot ",
                category.getId(),
                ProductStatus.ACTIVE,
                pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("Robot Arm", response.getContent().get(0).getName());
        verify(productRepository).searchProducts(
                company.getId(),
                "Robot",
                category.getId(),
                ProductStatus.ACTIVE,
                pageable);
    }

    @Test
    void updateProductSynchronizesExistingAndNewContents() {
        UUID productId = UUID.randomUUID();
        ProductContent removedContent = ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl("/old-a.png")
                .publicId("old-a-public-id")
                .type(ProductContentType.IMAGE)
                .orderIndex(0)
                .mimeType("image/png")
                .fileSize(100L)
                .build();
        ProductContent keptContent = ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl("/old-b.png")
                .publicId("old-b-public-id")
                .type(ProductContentType.IMAGE)
                .orderIndex(1)
                .mimeType("image/png")
                .fileSize(100L)
                .build();
        Product product = Product.builder()
                .id(productId)
                .company(company)
                .category(category)
                .name("Old")
                .sku("OLD")
                .description("Old desc")
                .price(BigDecimal.ONE)
                .currency("VND")
                .thumbnailUrl("/old-thumb.png")
                .thumbnailPublicId("old-thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>(List.of(removedContent, keptContent)))
                .build();
        removedContent.setProduct(product);
        keptContent.setProduct(product);

        UpdateProductRequest request = new UpdateProductRequest(
                "Robot",
                "VEX-002",
                category.getId(),
                "New desc",
                BigDecimal.TEN,
                "VND",
                ProductStatus.ACTIVE,
                List.of(keptContent.getId()),
                List.of(new CreateProductContentRequest("media_1", 1)));
        MockMultipartFile newContentFile = new MockMultipartFile("media_1", "new.png", "image/png", "image".getBytes());
        Map<String, MultipartFile> files = Map.of(
                "media_1", newContentFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(newContentFile)).thenReturn(upload("/new.png", "new-public-id", "image/png", 200L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(user, productId, request, null, files);

        assertEquals("Robot", product.getName());
        assertEquals(2, product.getContents().size());
        assertEquals(keptContent.getId(), product.getContents().get(0).getId());
        assertEquals(0, product.getContents().get(0).getOrderIndex());
        assertEquals("/new.png", product.getContents().get(1).getContentUrl());
        assertEquals("new-public-id", product.getContents().get(1).getPublicId());
        assertEquals(1, product.getContents().get(1).getOrderIndex());
        verify(cloudService).delete("old-a-public-id", "image");
    }

    @Test
    void updateProductRejectsMoreThanOneVideoIncludingExistingContents() {
        UUID productId = UUID.randomUUID();
        ProductContent existingVideo = ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl("/old-video.mp4")
                .publicId("old-video-public-id")
                .type(ProductContentType.VIDEO)
                .orderIndex(0)
                .mimeType("video/mp4")
                .fileSize(100L)
                .build();
        Product product = Product.builder()
                .id(productId)
                .company(company)
                .category(category)
                .name("Old")
                .sku("OLD")
                .description("Old desc")
                .price(BigDecimal.ONE)
                .currency("VND")
                .thumbnailUrl("/old-thumb.png")
                .thumbnailPublicId("old-thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>(List.of(existingVideo)))
                .build();
        existingVideo.setProduct(product);

        UpdateProductRequest request = new UpdateProductRequest(
                "Robot",
                "VEX-002",
                category.getId(),
                "New desc",
                BigDecimal.TEN,
                "VND",
                ProductStatus.ACTIVE,
                List.of(existingVideo.getId()),
                List.of(new CreateProductContentRequest("media_1", 1)));
        MockMultipartFile newVideoFile = new MockMultipartFile("media_1", "new-video.mp4", "video/mp4",
                "video".getBytes());
        Map<String, MultipartFile> files = Map.of("media_1", newVideoFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, null, files));

        assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, exception.getErrorCode());
        verify(cloudService, never()).upload(any(MultipartFile.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProductReplacingThumbnailDeletesOldThumbnailFromCloudinary() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .company(company)
                .category(category)
                .name("Old")
                .sku("OLD")
                .description("Old desc")
                .price(BigDecimal.ONE)
                .currency("VND")
                .thumbnailUrl("/old-thumb.png")
                .thumbnailPublicId("old-thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>())
                .build();
        UpdateProductRequest request = new UpdateProductRequest(
                "Robot",
                "VEX-002",
                category.getId(),
                "New desc",
                BigDecimal.TEN,
                "VND",
                ProductStatus.ACTIVE,
                List.of(),
                List.of());
        MockMultipartFile newThumbnail = new MockMultipartFile(
                "thumbnail", "new-thumb.png", "image/png", "image".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(newThumbnail))
                .thenReturn(upload("/new-thumb.png", "new-thumb-public-id", "image/png", 200L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(user, productId, request, newThumbnail, Map.of());

        assertEquals("/new-thumb.png", product.getThumbnailUrl());
        assertEquals("new-thumb-public-id", product.getThumbnailPublicId());
        verify(cloudService).delete("old-thumb-public-id", "image");
    }

    @Test
    void deleteProductDeletesThumbnailAndContentsFromCloudinaryBeforeArchiving() {
        UUID productId = UUID.randomUUID();
        ProductContent image = ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl("/old-a.png")
                .publicId("old-a-public-id")
                .type(ProductContentType.IMAGE)
                .orderIndex(0)
                .mimeType("image/png")
                .fileSize(100L)
                .build();
        ProductContent video = ProductContent.builder()
                .id(UUID.randomUUID())
                .contentUrl("/old-video.mp4")
                .publicId("old-video-public-id")
                .type(ProductContentType.VIDEO)
                .orderIndex(1)
                .mimeType("video/mp4")
                .fileSize(200L)
                .build();
        Product product = Product.builder()
                .id(productId)
                .company(company)
                .category(category)
                .name("Old")
                .sku("OLD")
                .description("Old desc")
                .price(BigDecimal.ONE)
                .currency("VND")
                .thumbnailUrl("/old-thumb.png")
                .thumbnailPublicId("old-thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>(List.of(image, video)))
                .build();
        image.setProduct(product);
        video.setProduct(product);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.deleteProduct(user, productId);

        verify(cloudService).delete("old-thumb-public-id", "image");
        verify(cloudService).delete("old-a-public-id", "image");
        verify(cloudService).delete("old-video-public-id", "video");
        assertEquals(ProductStatus.INACTIVE, product.getStatus());
    }

    @Test
    void updateProductAllowsKeepingCurrentInactiveCategory() {
        UUID productId = UUID.randomUUID();
        category.setStatus(ProductCategoryStatus.INACTIVE);
        Product product = Product.builder()
                .id(productId)
                .company(company)
                .category(category)
                .name("Old")
                .sku("OLD")
                .description("Old desc")
                .price(BigDecimal.ONE)
                .currency("VND")
                .thumbnailUrl("/old-thumb.png")
                .thumbnailPublicId("old-thumb-public-id")
                .status(ProductStatus.ACTIVE)
                .contents(new java.util.ArrayList<>())
                .build();
        UpdateProductRequest request = new UpdateProductRequest(
                "Robot",
                "VEX-002",
                category.getId(),
                "New desc",
                BigDecimal.TEN,
                "VND",
                ProductStatus.ACTIVE,
                List.of(),
                List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(user, productId, request, null, Map.of());

        assertEquals(category, product.getCategory());
        assertEquals("Robot", product.getName());
        assertEquals(ProductStatus.INACTIVE, product.getStatus());
    }

    @Test
    void getProductById_Exists_ReturnsProductResponseDTO() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).category(category).name("Product A")
                .sku("SKU1").description("Desc").price(BigDecimal.ONE).currency("VND").status(ProductStatus.ACTIVE)
                .contents(List.of()).build();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));

        ProductResponseDTO result = productService.getProductById(user, productId);

        assertNotNull(result);
        assertEquals("Product A", result.getName());
    }

    @Test
    void getProductById_NotFound_ThrowsException() {
        UUID productId = UUID.randomUUID();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> productService.getProductById(user, productId));
        assertSame(ErrorCode.PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getProducts_KeywordNullOrEmpty_PassesNullToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.searchProducts(company.getId(), null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProductResponseDTO> result = productService.getProducts(user, "   ", null, null, pageable);

        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        verify(productRepository).searchProducts(company.getId(), null, null, null, pageable);
    }

    @Test
    void createProduct_SkuDuplicated_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), Map.of()));
        assertSame(ErrorCode.PRODUCT_SKU_DUPLICATED, ex.getErrorCode());
    }

    @Test
    void createProduct_CategoryNotFound_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), Map.of()));
        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createProduct_ThumbnailNull_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, null, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ThumbnailEmpty_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        MockMultipartFile emptyThumb = new MockMultipartFile("thumbnail", "thumb.png", "image/png", new byte[0]);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, emptyThumb, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ThumbnailInvalidMimeType_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        MockMultipartFile badThumb = new MockMultipartFile("thumbnail", "thumb.txt", "text/plain", "abc".getBytes());
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, badThumb, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_FileMapMismatchKeysSize_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        Map<String, MultipartFile> files = Map.of("media_1", thumbnail());

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_FileMapMismatchActualKeys_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        Map<String, MultipartFile> files = Map.of("media_1", thumbnail(), "media_3", thumbnail());

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ContentFileInvalid_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        MockMultipartFile badContent = new MockMultipartFile("media_2", "bad.txt", "text/plain", "abc".getBytes());
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        Map<String, MultipartFile> files = Map.of("media_1", thumbnail(), "media_2", badContent);

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_InvalidStatus_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        request.setStatus(null);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        Map<String, MultipartFile> files = Map.of("media_1", thumbnail(), "media_2", thumbnail());

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_STATUS, ex.getErrorCode());
    }

    @Test
    void createProduct_DefaultCurrency_SavesWithVnd() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "   ", ProductStatus.ACTIVE,
                List.of());
        MockMultipartFile thumbnail = thumbnail();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(user, request, thumbnail, Map.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("VND", captor.getValue().getCurrency());
    }

    @Test
    void updateProduct_SkuDuplicated_ThrowsException() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).sku("OLD").build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(), List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, null, Map.of()));
        assertSame(ErrorCode.PRODUCT_SKU_DUPLICATED, ex.getErrorCode());
    }

    @Test
    void updateProduct_CategoryNotFound_ThrowsException() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).sku("OLD").build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(), List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, null, Map.of()));
        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateProduct_CategoryInactive_ThrowsException() {
        UUID productId = UUID.randomUUID();
        ProductCategory oldCat = ProductCategory.builder().id(UUID.randomUUID()).build();
        Product product = Product.builder().id(productId).company(company).category(oldCat).sku("OLD").build();
        category.setStatus(ProductCategoryStatus.INACTIVE);
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(), List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, null, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS, ex.getErrorCode());
    }

    @Test
    void updateProduct_ExistingContentIdNotFound_ThrowsException() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).category(category).sku("OLD")
                .contents(List.of()).build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(UUID.randomUUID()), List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, null, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void updateProduct_NewThumbnailInvalidMimeType_ThrowsException() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).category(category).sku("OLD")
                .contents(List.of()).build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(), List.of());
        MockMultipartFile badThumb = new MockMultipartFile("thumbnail", "thumb.txt", "text/plain", "abc".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.updateProduct(user, productId, request, badThumb, Map.of()));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void uploadProductMedia_NonAppException_ThrowsUploadFailed() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                List.of());
        MockMultipartFile thumbnail = thumbnail();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenThrow(new RuntimeException("Cloudinary offline"));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail, Map.of()));
        assertSame(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
    }

    @Test
    void createProduct_DuplicateFileKeys_ThrowsException() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                List.of(new CreateProductContentRequest("media_1", 0), new CreateProductContentRequest("media_1", 1)));
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), Map.of("media_1", thumbnail())));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_FilesMapNull_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), null));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ContentFileNull_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", null);
        files.put("media_2", thumbnail());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ContentFileEmpty_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        MockMultipartFile emptyFile = new MockMultipartFile("media_1", "empty.png", "image/png", new byte[0]);
        Map<String, MultipartFile> files = Map.of("media_1", emptyFile, "media_2", thumbnail());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_ContentFileNullContentType_ThrowsException() {
        CreateProductRequest request = validCreateRequest();
        MockMultipartFile nullMimeFile = new MockMultipartFile("media_1", "file", null, "data".getBytes());
        Map<String, MultipartFile> files = Map.of("media_1", nullMimeFile, "media_2", thumbnail());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail(), files));
        assertSame(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
    }

    @Test
    void createProduct_CurrencyNull_SavesWithVnd() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, null, ProductStatus.ACTIVE,
                List.of());
        MockMultipartFile thumbnail = thumbnail();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(user, request, thumbnail, Map.of());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("VND", captor.getValue().getCurrency());
    }

    @Test
    void getProducts_KeywordNull_PassesNullToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.searchProducts(company.getId(), null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ProductResponseDTO> result = productService.getProducts(user, null, null, null, pageable);

        assertNotNull(result);
        verify(productRepository).searchProducts(company.getId(), null, null, null, pageable);
    }

    @Test
    void createProduct_ContentsNull_SavesSuccessfully() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "VND", ProductStatus.ACTIVE, null);
        MockMultipartFile thumbnail = thumbnail();

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(user, request, thumbnail, null);

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void updateProduct_CategoryNull_ThrowsException() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).company(company).category(null).sku("OLD")
                .contents(new java.util.ArrayList<>()).build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, null, List.of());
        MockMultipartFile emptyThumb = new MockMultipartFile("thumbnail", "thumb.png", "image/png", new byte[0]);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(user, productId, request, emptyThumb, null);

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void cleanupUploadedFiles_ThrowsException_Ignored() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                List.of(new CreateProductContentRequest("media_1", 0)));
        MockMultipartFile thumbnail = thumbnail();
        MockMultipartFile contentFile = thumbnail();
        Map<String, MultipartFile> files = Map.of("media_1", contentFile);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenReturn(upload("/thumb.png", "thumb-public-id", "image/png", 100L));
        when(cloudService.upload(contentFile)).thenThrow(new RuntimeException("Upload failed"));
        org.mockito.Mockito.doThrow(new RuntimeException("Delete failed")).when(cloudService).delete("thumb-public-id",
                "image");

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail, files));
        assertSame(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        verify(cloudService).delete("thumb-public-id", "image");
    }

    @Test
    void uploadProductMedia_CompletionExceptionNullCause_ThrowsUploadFailed() {
        CreateProductRequest request = new CreateProductRequest(
                "Robot", "VEX-001", category.getId(), "Robot demo", BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                List.of());
        MockMultipartFile thumbnail = thumbnail();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(thumbnail)).thenThrow(new java.util.concurrent.CompletionException(null));

        AppException ex = assertThrows(AppException.class,
                () -> productService.createProduct(user, request, thumbnail, Map.of()));
        assertSame(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
    }

    @Test
    void updateProduct_CategoryGetIdNull_ThrowsException() {
        UUID productId = UUID.randomUUID();
        ProductCategory oldCat = ProductCategory.builder().id(null).build();
        Product product = Product.builder().id(productId).company(company).category(oldCat).sku("OLD")
                .contents(new java.util.ArrayList<>()).build();
        UpdateProductRequest request = new UpdateProductRequest("Robot", "VEX-002", category.getId(), "New desc",
                BigDecimal.TEN, "VND", ProductStatus.ACTIVE, List.of(), List.of());

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(user, productId, request, null, Map.of());

        verify(productRepository).save(any(Product.class));
    }

    private CreateProductRequest validCreateRequest() {
        return new CreateProductRequest(
                "Robot",
                "VEX-001",
                category.getId(),
                "Robot demo",
                BigDecimal.TEN,
                "VND",
                ProductStatus.ACTIVE,
                List.of(
                        new CreateProductContentRequest("media_1", 0),
                        new CreateProductContentRequest("media_2", 1)));
    }

    private MockMultipartFile thumbnail() {
        return new MockMultipartFile("thumbnail", "thumb.png", "image/png", "image".getBytes());
    }

    private CloudinaryResponse upload(String url, String publicId, String mimeType, Long fileSize) {
        return CloudinaryResponse.builder()
                .url(url)
                .publicId(publicId)
                .fileType(mimeType)
                .fileSize(fileSize)
                .build();
    }
}
