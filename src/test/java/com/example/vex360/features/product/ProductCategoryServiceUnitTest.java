package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.product.dtos.request.CreateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryStatusRequest;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductCategoryMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.product.services.ProductCategoryService;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.ProductCategory;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceUnitTest {
    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ProductRepository productRepository;

    private ProductCategoryService productCategoryService;
    private User user;
    private Company company;

    @BeforeEach
    void setup() {
        productCategoryService = new ProductCategoryService(
                companyRepository,
                productCategoryRepository,
                productRepository,
                new ProductCategoryMapper());
        user = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Orion").build();
    }

    @Test
    void createCategoryRejectsDuplicateNameInSameCompany() {
        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productCategoryRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), "Máy tính"))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> productCategoryService.createCategory(user,
                        new CreateProductCategoryRequest("Máy tính", "Laptop và desktop")));

        assertEquals(ErrorCode.PRODUCT_CATEGORY_NAME_DUPLICATED, exception.getErrorCode());
        verify(productCategoryRepository, never()).save(any(ProductCategory.class));
    }

    @Test
    void createCategoryStoresActiveCategoryForCurrentCompany() {
        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productCategoryRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), "Máy tính"))
                .thenReturn(false);
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
            ProductCategory category = invocation.getArgument(0);
            category.setId(UUID.randomUUID());
            return category;
        });

        productCategoryService.createCategory(user,
                new CreateProductCategoryRequest(" Máy tính ", " Laptop và desktop "));

        ArgumentCaptor<ProductCategory> categoryCaptor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepository).save(categoryCaptor.capture());
        ProductCategory savedCategory = categoryCaptor.getValue();
        assertEquals(company, savedCategory.getCompany());
        assertEquals("Máy tính", savedCategory.getName());
        assertEquals(" Laptop và desktop ", savedCategory.getDescription());
        assertEquals(ProductCategoryStatus.ACTIVE, savedCategory.getStatus());
    }

    @Test
    void updateCategoryRejectsDuplicateNameInSameCompany() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .company(company)
                .name("Cũ")
                .status(ProductCategoryStatus.ACTIVE)
                .build();

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.of(category));
        when(productCategoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), "Mới", categoryId))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> productCategoryService.updateCategory(user, categoryId,
                        new UpdateProductCategoryRequest("Mới", "Desc")));

        assertEquals(ErrorCode.PRODUCT_CATEGORY_NAME_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void updateCategoryStatusSetsInactiveWithoutDeletingCategory() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .company(company)
                .name("Máy tính")
                .status(ProductCategoryStatus.ACTIVE)
                .build();

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.of(category));
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productCategoryService.updateCategoryStatus(user, categoryId,
                new UpdateProductCategoryStatusRequest(ProductCategoryStatus.INACTIVE));

        assertEquals(ProductCategoryStatus.INACTIVE, category.getStatus());
        verify(productCategoryRepository).save(category);
    }

    @Test
    void updateCategoryStatusToInactiveSetsProductsInactiveInCategory() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(categoryId)
                .company(company)
                .name("MÃ¡y tÃ­nh")
                .status(ProductCategoryStatus.ACTIVE)
                .build();

        when(companyRepository.findByOwnerUserId(user.getId())).thenReturn(Optional.of(company));
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.of(category));
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productCategoryService.updateCategoryStatus(user, categoryId,
                new UpdateProductCategoryStatusRequest(ProductCategoryStatus.INACTIVE));

        verify(productRepository).updateStatusByCategoryIdAndCompanyId(
                categoryId,
                company.getId(),
                ProductStatus.INACTIVE);
    }
}
