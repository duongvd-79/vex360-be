package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.example.vex360.features.company.repositories.CompanyRepository;
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
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.Product;
import com.example.vex360.shared.entities.ProductCategory;
import com.example.vex360.shared.entities.ProductContent;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {
    @Mock
    private CompanyRepository companyRepository;

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
                companyRepository,
                productCategoryRepository,
                productRepository,
                cloudService,
                new ProductMapper());
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
        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
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
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes()));
        files.put("media_2", new MockMultipartFile("media_2", "demo.mp4", "video/mp4", "video".getBytes()));

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(any(MultipartFile.class)))
                .thenReturn(upload("/thumb.png", "image/png", 100L))
                .thenReturn(upload("/front.png", "image/png", 200L))
                .thenReturn(upload("/demo.mp4", "video/mp4", 300L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(UUID.randomUUID());
            product.getContents().forEach(content -> content.setId(UUID.randomUUID()));
            return product;
        });

        productService.createProduct(user, validCreateRequest(), thumbnail(), files);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertEquals(company, savedProduct.getCompany());
        assertEquals(category, savedProduct.getCategory());
        assertEquals("/thumb.png", savedProduct.getThumbnailUrl());
        assertEquals("public-id", savedProduct.getThumbnailPublicId());
        assertEquals(2, savedProduct.getContents().size());
        assertEquals("public-id", savedProduct.getContents().get(0).getPublicId());
        assertEquals(ProductContentType.IMAGE, savedProduct.getContents().get(0).getType());
        assertEquals(ProductContentType.VIDEO, savedProduct.getContents().get(1).getType());
    }

    @Test
    void createProductUsesRequestedStatus() {
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put("media_1", new MockMultipartFile("media_1", "front.png", "image/png", "image".getBytes()));
        files.put("media_2", new MockMultipartFile("media_2", "demo.mp4", "video/mp4", "video".getBytes()));
        CreateProductRequest request = validCreateRequest();
        request.setStatus(ProductStatus.INACTIVE);

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(any(MultipartFile.class)))
                .thenReturn(upload("/thumb.png", "image/png", 100L))
                .thenReturn(upload("/front.png", "image/png", 200L))
                .thenReturn(upload("/demo.mp4", "video/mp4", 300L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(user, request, thumbnail(), files);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals(ProductStatus.INACTIVE, productCaptor.getValue().getStatus());
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

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
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
        Map<String, MultipartFile> files = Map.of(
                "media_1", new MockMultipartFile("media_1", "new.png", "image/png", "image".getBytes()));

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(any(MultipartFile.class))).thenReturn(upload("/new.png", "image/png", 200L));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(user, productId, request, null, files);

        assertEquals("Robot", product.getName());
        assertEquals(2, product.getContents().size());
        assertEquals(keptContent.getId(), product.getContents().get(0).getId());
        assertEquals(0, product.getContents().get(0).getOrderIndex());
        assertEquals("/new.png", product.getContents().get(1).getContentUrl());
        assertEquals(1, product.getContents().get(1).getOrderIndex());
        verify(cloudService).delete("old-a-public-id", "image");
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

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(cloudService.upload(newThumbnail)).thenReturn(upload("/new-thumb.png", "new-thumb-public-id", "image/png", 200L));
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

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.deleteProduct(user, productId);

        verify(cloudService).delete("old-thumb-public-id", "image");
        verify(cloudService).delete("old-a-public-id", "image");
        verify(cloudService).delete("old-video-public-id", "video");
        assertEquals(ProductStatus.ARCHIVED, product.getStatus());
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

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productRepository.findByIdAndCompanyId(productId, company.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                .thenReturn(false);
        when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(user, productId, request, null, Map.of());

        assertEquals(category, product.getCategory());
        assertEquals("Robot", product.getName());
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

    private CloudinaryResponse upload(String url, String mimeType, Long fileSize) {
        return upload(url, "public-id", mimeType, fileSize);
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
