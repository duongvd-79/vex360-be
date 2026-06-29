package com.example.vex360.features.product.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.product.dtos.request.CreateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryStatusRequest;
import com.example.vex360.features.product.dtos.response.ProductCategoryResponseDTO;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.mapper.ProductCategoryMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.ProductCategory;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    private final CompanyRepository companyRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryMapper productCategoryMapper;

    @Transactional(readOnly = true)
    public List<ProductCategoryResponseDTO> getCategories(User currentUser, ProductCategoryStatus status) {
        Company company = getCompanyForCurrentUser(currentUser);
        List<ProductCategory> categories = status == null
                ? productCategoryRepository.findByCompanyIdOrderByNameAsc(company.getId())
                : productCategoryRepository.findByCompanyIdAndStatusOrderByNameAsc(company.getId(), status);
        return categories.stream()
                .map(productCategoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductCategoryResponseDTO createCategory(User currentUser, CreateProductCategoryRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        String name = request.getName().trim();
        if (productCategoryRepository.existsByCompanyIdAndNameIgnoreCase(company.getId(), name)) {
            throw new AppException(ErrorCode.PRODUCT_CATEGORY_NAME_DUPLICATED);
        }

        ProductCategory category = ProductCategory.builder()
                .company(company)
                .name(name)
                .description(request.getDescription())
                .status(ProductCategoryStatus.ACTIVE)
                .build();
        return productCategoryMapper.toResponse(productCategoryRepository.save(category));
    }

    @Transactional
    public ProductCategoryResponseDTO updateCategory(
            User currentUser,
            UUID categoryId,
            UpdateProductCategoryRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        ProductCategory category = getCategoryForCompany(categoryId, company);
        String name = request.getName().trim();
        if (productCategoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), name, categoryId)) {
            throw new AppException(ErrorCode.PRODUCT_CATEGORY_NAME_DUPLICATED);
        }

        category.setName(name);
        category.setDescription(request.getDescription());
        return productCategoryMapper.toResponse(productCategoryRepository.save(category));
    }

    @Transactional
    public ProductCategoryResponseDTO updateCategoryStatus(
            User currentUser,
            UUID categoryId,
            UpdateProductCategoryStatusRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        ProductCategory category = getCategoryForCompany(categoryId, company);
        category.setStatus(request.getStatus());
        if (request.getStatus() == ProductCategoryStatus.INACTIVE) {
            productRepository.updateStatusByCategoryIdAndCompanyId(
                    categoryId,
                    company.getId(),
                    ProductStatus.ARCHIVED);
        }
        return productCategoryMapper.toResponse(productCategoryRepository.save(category));
    }

    private ProductCategory getCategoryForCompany(UUID categoryId, Company company) {
        return productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }
}
