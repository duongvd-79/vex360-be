package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.events.ProductDeletedEvent;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.dtos.PageResponse;

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

    private ProductService service;
    private User user;
    private Company company;
    private Product product;

    @BeforeEach
    void setup() {
        service = new ProductService(companyService, companyStorageService,
                categoryRepository, productRepository,
                cloudService, productMapper, eventPublisher);
        user = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).build();
        ProductContent content = ProductContent.builder().id(UUID.randomUUID()).publicId("content-id").fileSize(0L)
                .type(ProductContentType.VIDEO).build();
        product = Product.builder().id(UUID.randomUUID()).company(company).thumbnailPublicId("thumbnail-id")
                .status(ProductStatus.ACTIVE).contents(List.of(content)).build();
        content.setProduct(product);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        lenient().when(productRepository.findByIdAndCompanyId(product.getId(),
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
                "PENDING")).thenReturn(true);

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
                "PENDING")).thenReturn(false);
        when(productRepository.save(product)).thenReturn(product);

        service.deleteProduct(user, product.getId());

        verify(cloudService).delete("thumbnail-id", "image");
        verify(cloudService).delete("content-id", "video");
        verify(eventPublisher).publishEvent(any(ProductDeletedEvent.class));
    }
}
