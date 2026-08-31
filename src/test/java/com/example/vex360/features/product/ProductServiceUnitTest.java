package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.dtos.request.CreateProductContentPreUploadedRequest;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.events.ProductDeletedEvent;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.services.R2StorageService;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {
    @Mock
    CompanyService companyService;
    @Mock
    CompanyStorageService companyStorageService;
    @Mock
    ProductCategoryRepository categoryRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    CloudService cloudService;
    @Mock
    ProductMapper productMapper;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    R2StorageService r2StorageService;

    private ProductService service;
    private User user;
    private Company company;
    private Product product;

    @BeforeEach
    void setup() {
        service = new ProductService(companyService, companyStorageService,
                categoryRepository, productRepository,
                cloudService, productMapper, eventPublisher, r2StorageService);
        user = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).build();
        ProductContent content = ProductContent.builder().id(UUID.randomUUID()).publicId("content-id").fileSize(0L)
                .type(ProductContentType.VIDEO).build();
        product = Product.builder().id(UUID.randomUUID()).company(company).thumbnailPublicId("thumbnail-id")
                .status(ProductStatus.ACTIVE).contents(new ArrayList<>(List.of(content))).build();
        content.setProduct(product);
        // lenient() vì một số test (createProduct_*) không đi qua stub này —
        // Mockito strict mode sẽ báo UnnecessaryStubbingException nếu dùng when()
        // thường.
        lenient().when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        lenient().when(productRepository.findByIdAndCompanyId(product.getId(),
                company.getId())).thenReturn(Optional.of(product));
        lenient().when(productRepository.findByIdAndCompanyIdForUpdate(product.getId(),
                company.getId())).thenReturn(Optional.of(product));
    }

    @Test
    void getProductsNormalizesKeywordAndMapsCategorySortAlias() {
        Pageable pageable = PageRequest.of(1, 10, Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.desc("categoryName"),
                Sort.Order.asc("price"),
                Sort.Order.desc("sku")));
        Pageable mappedPageable = PageRequest.of(1, 10, Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.desc("category.name"),
                Sort.Order.asc("price"),
                Sort.Order.desc("sku")));
        when(productRepository.searchProducts(
                eq(company.getId()), eq("chair"), eq(null), eq(ProductStatus.ACTIVE), eq(mappedPageable)))
                .thenReturn(Page.empty(mappedPageable));

        PageResponse<?> response = service.getProducts(
                user, "  chair  ", null, ProductStatus.ACTIVE, pageable);

        assertEquals(1, response.getPage());
        assertEquals(10, response.getSize());
    }

    @Test
    void pendingBoothPreventsProductAndContentDeletion() {
        when(productRepository.existsInBoothWithStatus(product.getId(),
                List.of("PENDING", "PUBLISHED"))).thenReturn(1L);

        AppException exception = assertThrows(AppException.class,
                () -> service.deleteProduct(user, product.getId()));

        assertSame(ErrorCode.PRODUCT_USED_BY_PENDING_BOOTH,
                exception.getErrorCode());
        verify(cloudService, never()).delete("thumbnail-id", "image");
        verify(cloudService, never()).delete("content-id", "video");
    }

    @Test
    void reviewHistoryDoesNotPreventPhysicalFileDeletion() {
        when(productRepository.existsInBoothWithStatus(product.getId(),
                List.of("PENDING", "PUBLISHED"))).thenReturn(0L);
        when(productRepository.save(product)).thenReturn(product);

        service.deleteProduct(user, product.getId());

        verify(cloudService).delete("thumbnail-id", "image");
        verify(cloudService).delete("content-id", "video");
        verify(eventPublisher).publishEvent(any(ProductDeletedEvent.class));
    }

    // ================= getProducts =================

    @Test
    void getProducts_Success_ReturnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product));
        ProductResponseDTO dto = new ProductResponseDTO();
        when(productRepository.searchProducts(company.getId(), "abc", null, ProductStatus.ACTIVE, pageable))
                .thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(dto);

        PageResponse<ProductResponseDTO> result = service.getProducts(user, "abc", null, ProductStatus.ACTIVE,
                pageable);

        assertEquals(1, result.getContent().size());
        assertSame(dto, result.getContent().get(0));
    }

    // ================= getProductById =================

    @Test
    void getProductById_Success_ReturnsProduct() {
        ProductResponseDTO dto = new ProductResponseDTO();
        when(productMapper.toResponse(product)).thenReturn(dto);

        ProductResponseDTO result = service.getProductById(user, product.getId());

        assertSame(dto, result);
    }

    @Test
    void getProductById_ProductNotFound_ThrowsProductNotFound() {
        UUID missingId = UUID.randomUUID();
        when(productRepository.findByIdAndCompanyId(missingId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.getProductById(user, missingId));

        assertSame(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    // ================= createProduct =================

    @Test
    void createProduct_Success_SavesProductWithContents() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        CreateProductRequest request = new CreateProductRequest();
        request.setName("New Product");
        request.setSku("NEW-SKU");
        request.setCategoryId(category.getId());
        request.setDescription("Description");
        request.setPrice(BigDecimal.TEN);
        request.setStatus(ProductStatus.ACTIVE);
        request.setThumbnailUrl("https://thumb");
        request.setThumbnailPublicId("thumb-id");
        request.setContents(List.of());

        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "NEW-SKU")).thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createProduct(user, request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("New Product", captor.getValue().getName());
        assertEquals(ProductStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void createProduct_DuplicateSku_ThrowsProductSkuDuplicated() {
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("DUP-SKU");
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "DUP-SKU")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.createProduct(user, request));

        assertSame(ErrorCode.PRODUCT_SKU_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void createProduct_CategoryNotFound_ThrowsProductCategoryNotFound() {
        UUID categoryId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-X");
        request.setCategoryId(categoryId);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "SKU-X")).thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.createProduct(user, request));

        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void createProduct_InactiveCategory_ThrowsInvalidProductCategoryStatus() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.INACTIVE).build();
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-Y");
        request.setCategoryId(category.getId());
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "SKU-Y")).thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> service.createProduct(user, request));

        assertSame(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS, exception.getErrorCode());
    }

    @Test
    void createProduct_TooManyImages_ThrowsInvalidProductMedia() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-Z");
        request.setCategoryId(category.getId());
        List<CreateProductContentPreUploadedRequest> contents = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CreateProductContentPreUploadedRequest c = new CreateProductContentPreUploadedRequest();
            c.setContentUrl("url" + i);
            c.setPublicId("pid" + i);
            c.setMimeType("image/png");
            c.setOrderIndex(i);
            contents.add(c);
        }
        request.setContents(contents);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "SKU-Z")).thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> service.createProduct(user, request));

        assertSame(ErrorCode.PRODUCT_MEDIA_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void createProduct_NullStatus_ThrowsInvalidProductStatus() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-N");
        request.setCategoryId(category.getId());
        request.setContents(List.of());
        request.setStatus(null);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "SKU-N")).thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> service.createProduct(user, request));

        assertSame(ErrorCode.INVALID_PRODUCT_STATUS, exception.getErrorCode());
    }

    // ================= updateProduct =================

    @Test
    void updateProduct_Success_UpdatesFieldsAndSaves() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        product.setCategory(category);
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Updated Name");
        request.setSku("SKU-1");
        request.setCategoryId(category.getId());
        request.setDescription("Updated description");
        request.setPrice(BigDecimal.TEN);
        request.setStatus(ProductStatus.ACTIVE);
        request.setExistingContentIds(List.of(product.getContents().get(0).getId()));
        request.setNewContents(List.of());

        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "SKU-1", product.getId()))
                .thenReturn(false);
        when(productRepository.save(product)).thenReturn(product);

        service.updateProduct(user, product.getId(), request);

        assertEquals("Updated Name", product.getName());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_LockedByDesignRequest_ThrowsDesignProductLocked() {
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(1L);

        AppException exception = assertThrows(AppException.class,
                () -> service.updateProduct(user, product.getId(), new UpdateProductRequest()));

        assertSame(ErrorCode.DESIGN_PRODUCT_LOCKED, exception.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_UsedByPendingBooth_ThrowsProductUsedByPendingBooth() {
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(1L);

        AppException exception = assertThrows(AppException.class,
                () -> service.updateProduct(user, product.getId(), new UpdateProductRequest()));

        assertSame(ErrorCode.PRODUCT_USED_BY_PENDING_BOOTH, exception.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_DuplicateSku_ThrowsProductSkuDuplicated() {
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        UpdateProductRequest request = new UpdateProductRequest();
        request.setSku("DUP-SKU");
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "DUP-SKU", product.getId()))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.updateProduct(user, product.getId(), request));

        assertSame(ErrorCode.PRODUCT_SKU_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void updateProduct_CategoryNotFound_ThrowsProductCategoryNotFound() {
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        UUID categoryId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest();
        request.setSku("SKU-1");
        request.setCategoryId(categoryId);
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "SKU-1", product.getId()))
                .thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.updateProduct(user, product.getId(), request));

        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateProduct_InvalidExistingContentId_ThrowsInvalidProductMedia() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        product.setCategory(category);
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        UpdateProductRequest request = new UpdateProductRequest();
        request.setSku("SKU-1");
        request.setCategoryId(category.getId());
        request.setExistingContentIds(List.of(UUID.randomUUID()));
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "SKU-1", product.getId()))
                .thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));

        AppException exception = assertThrows(AppException.class,
                () -> service.updateProduct(user, product.getId(), request));

        assertSame(ErrorCode.PRODUCT_MEDIA_REFERENCE_INVALID, exception.getErrorCode());
    }

    @Test
    void updateProduct_CategoryInactive_ForcesStatusInactive() {
        ProductCategory inactiveCategory = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.INACTIVE).build();
        product.setCategory(inactiveCategory);
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Name");
        request.setSku("SKU-1");
        request.setCategoryId(inactiveCategory.getId());
        request.setDescription("Desc");
        request.setPrice(BigDecimal.TEN);
        request.setStatus(ProductStatus.ACTIVE);
        request.setExistingContentIds(List.of(product.getContents().get(0).getId()));
        request.setNewContents(List.of());
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "SKU-1", product.getId()))
                .thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(inactiveCategory.getId(), company.getId()))
                .thenReturn(Optional.of(inactiveCategory));
        when(productRepository.save(product)).thenReturn(product);

        service.updateProduct(user, product.getId(), request);

        assertEquals(ProductStatus.INACTIVE, product.getStatus());
    }

    @Test
    void updateProduct_ThumbnailReplaced_DeductsOldUsageAndDeletesOldFile() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        product.setCategory(category);
        product.setThumbnailFileSize(500L);
        when(productRepository.existsLockedByDesignRequest(product.getId())).thenReturn(0L);
        when(productRepository.existsInBoothWithStatus(product.getId(), List.of("PENDING", "PUBLISHED")))
                .thenReturn(0L);
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("Name");
        request.setSku("SKU-1");
        request.setCategoryId(category.getId());
        request.setDescription("Desc");
        request.setPrice(BigDecimal.TEN);
        request.setStatus(ProductStatus.ACTIVE);
        request.setThumbnailUrl("https://new-thumb");
        request.setThumbnailPublicId("new-thumb-id");
        request.setThumbnailFileSize(800L);
        request.setExistingContentIds(List.of(product.getContents().get(0).getId()));
        request.setNewContents(List.of());
        when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "SKU-1", product.getId()))
                .thenReturn(false);
        when(categoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.save(product)).thenReturn(product);

        service.updateProduct(user, product.getId(), request);

        verify(companyStorageService).deductUsage(company, 500L);
        verify(cloudService).delete("thumbnail-id", "image");
        assertEquals("https://new-thumb", product.getThumbnailUrl());
        assertEquals("new-thumb-id", product.getThumbnailPublicId());
        assertEquals(800L, product.getThumbnailFileSize());
    }
}
