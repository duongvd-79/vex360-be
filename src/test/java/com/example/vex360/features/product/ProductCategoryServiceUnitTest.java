package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.dtos.request.CreateProductCategoryRequest;
import com.example.vex360.features.product.dtos.response.ProductCategoryResponseDTO;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryStatusRequest;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductCategoryMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.features.product.services.ProductCategoryService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceUnitTest {
    @Mock
    private CompanyService companyService;

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
                companyService,
                productCategoryRepository,
                productRepository,
                new ProductCategoryMapper());
        user = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Orion").build();
    }

    @Test
    void createCategoryRejectsDuplicateNameInSameCompany() {
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
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
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
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

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
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

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
        when(productCategoryRepository.save(any(ProductCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
        when(productCategoryRepository.save(any(ProductCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productCategoryService.updateCategoryStatus(user, categoryId,
                new UpdateProductCategoryStatusRequest(ProductCategoryStatus.INACTIVE));

        verify(productRepository).updateStatusByCategoryIdAndCompanyId(
                categoryId,
                company.getId(),
                ProductStatus.INACTIVE);
    }

    @Test
    void updateCategoryStatusDoesNotMutateProductsUsedByPendingBooth() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(categoryId).company(company).name("Pending products")
                .status(ProductCategoryStatus.ACTIVE).build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
        when(productRepository.existsCategoryProductInBoothWithStatus(categoryId, company.getId(), "PENDING"))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> productCategoryService.updateCategoryStatus(
                user, categoryId, new UpdateProductCategoryStatusRequest(ProductCategoryStatus.INACTIVE)));

        assertSame(ErrorCode.PRODUCT_USED_BY_PENDING_BOOTH, exception.getErrorCode());
        verify(productRepository, never()).updateStatusByCategoryIdAndCompanyId(
                categoryId, company.getId(), ProductStatus.INACTIVE);
    }

    @Test
    void getCategories_StatusNull_ReturnsAllCategories() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).name("Electronics").company(company)
                .build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByCompanyIdOrderByNameAsc(company.getId())).thenReturn(List.of(category));

        List<ProductCategoryResponseDTO> result = productCategoryService.getCategories(user, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Electronics", result.get(0).getName());
    }

    @Test
    void getCategories_StatusActive_ReturnsActiveCategoriesOnly() {
        ProductCategory category = ProductCategory.builder().id(UUID.randomUUID()).name("Electronics").company(company)
                .status(ProductCategoryStatus.ACTIVE).build();
        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByCompanyIdAndStatusOrderByNameAsc(company.getId(),
                ProductCategoryStatus.ACTIVE)).thenReturn(List.of(category));

        List<ProductCategoryResponseDTO> result = productCategoryService.getCategories(user,
                ProductCategoryStatus.ACTIVE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Electronics", result.get(0).getName());
    }

    @Test
    void updateCategory_Success_SavesAndReturnsCategory() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder().id(categoryId).company(company).name("Old Name")
                .status(ProductCategoryStatus.ACTIVE).build();
        UpdateProductCategoryRequest request = new UpdateProductCategoryRequest("New Name", "New Description");

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
        when(productCategoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), "New Name",
                categoryId)).thenReturn(false);
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCategoryResponseDTO result = productCategoryService.updateCategory(user, categoryId, request);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
        assertEquals("New Description", category.getDescription());
    }

    @Test
    void updateCategory_NotFound_ThrowsException() {
        UUID categoryId = UUID.randomUUID();
        UpdateProductCategoryRequest request = new UpdateProductCategoryRequest("New Name", "New Description");

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> productCategoryService.updateCategory(user, categoryId, request));
        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateCategoryStatus_ToActive_DoesNotCallProductRepo() {
        UUID categoryId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder().id(categoryId).company(company).name("Electronics")
                .status(ProductCategoryStatus.INACTIVE).build();
        UpdateProductCategoryStatusRequest request = new UpdateProductCategoryStatusRequest(
                ProductCategoryStatus.ACTIVE);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId()))
                .thenReturn(Optional.of(category));
        when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCategoryResponseDTO result = productCategoryService.updateCategoryStatus(user, categoryId, request);

        assertNotNull(result);
        assertEquals(ProductCategoryStatus.ACTIVE, category.getStatus());
        verify(productRepository, never()).updateStatusByCategoryIdAndCompanyId(any(), any(), any());
    }

    @Test
    void updateCategoryStatus_NotFound_ThrowsException() {
        UUID categoryId = UUID.randomUUID();
        UpdateProductCategoryStatusRequest request = new UpdateProductCategoryStatusRequest(
                ProductCategoryStatus.ACTIVE);

        when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
        when(productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> productCategoryService.updateCategoryStatus(user, categoryId, request));
        assertSame(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND, exception.getErrorCode());
    }
}
