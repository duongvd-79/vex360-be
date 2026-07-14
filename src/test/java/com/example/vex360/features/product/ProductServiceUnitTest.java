package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {
    @Mock CompanyService companyService;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock CloudService cloudService;
    @Mock ProductMapper productMapper;

    private ProductService service;
    private User user;
    private Company company;
    private Product product;

    @BeforeEach
    void setup() {
        service = new ProductService(companyService, categoryRepository, productRepository, cloudService, productMapper);
        user = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).build();
        ProductContent content = ProductContent.builder().id(UUID.randomUUID()).publicId("content-id")
                .type(ProductContentType.VIDEO).build();
        product = Product.builder().id(UUID.randomUUID()).company(company).thumbnailPublicId("thumbnail-id")
                .status(ProductStatus.ACTIVE).contents(List.of(content)).build();
        content.setProduct(product);
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productRepository.findByIdAndCompanyId(product.getId(), company.getId())).thenReturn(Optional.of(product));
    }

    @Test
    void pendingBoothPreventsProductAndContentDeletion() {
        when(productRepository.existsInBoothWithStatus(product.getId(), "PENDING")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> service.deleteProduct(user, product.getId()));

        assertSame(ErrorCode.PRODUCT_USED_BY_PENDING_BOOTH, exception.getErrorCode());
        verify(cloudService, never()).delete("thumbnail-id", "image");
        verify(cloudService, never()).delete("content-id", "video");
    }

    @Test
    void reviewHistoryDoesNotPreventPhysicalFileDeletion() {
        when(productRepository.existsInBoothWithStatus(product.getId(), "PENDING")).thenReturn(false);
        when(productRepository.save(product)).thenReturn(product);

        service.deleteProduct(user, product.getId());

        verify(cloudService).delete("thumbnail-id", "image");
        verify(cloudService).delete("content-id", "video");
    }
}
