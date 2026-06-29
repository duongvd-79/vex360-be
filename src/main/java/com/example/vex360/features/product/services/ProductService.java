package com.example.vex360.features.product.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final int MAX_CONTENT_COUNT = 10;
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");

    private final CompanyRepository companyRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final CloudService cloudService;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getProducts(User currentUser, Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<ProductResponseDTO> products = productRepository
                .findByCompanyIdAndStatusNotOrderByCreatedAtDesc(company.getId(), ProductStatus.ARCHIVED, pageable)
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return productMapper.toResponse(getProductForCompany(productId, company));
    }

    @Transactional
    public ProductResponseDTO createProduct(
            User currentUser,
            CreateProductRequest request,
            MultipartFile thumbnail,
            Map<String, MultipartFile> files) {
        Company company = getCompanyForCurrentUser(currentUser);
        String sku = request.getSku().trim();
        if (productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), sku)) {
            throw new AppException(ErrorCode.PRODUCT_SKU_DUPLICATED);
        }

        ProductCategory category = getActiveCategoryForCompany(request.getCategoryId(), company);
        validateThumbnail(thumbnail);
        List<CreateProductContentRequest> contentRequests = safeCreateContents(request.getContents());
        validateContentCount(contentRequests.size());
        validateFileMap(contentRequests, files);

        CloudinaryResponse thumbnailUpload = cloudService.upload(thumbnail);
        Product product = Product.builder()
                .company(company)
                .category(category)
                .name(request.getName().trim())
                .sku(sku)
                .description(request.getDescription().trim())
                .price(request.getPrice())
                .currency(normalizeCurrency(request.getCurrency()))
                .thumbnailUrl(thumbnailUpload.getUrl())
                .thumbnailPublicId(thumbnailUpload.getPublicId())
                .isVisible(resolveIsVisible(request.getIsVisible()))
                .status(resolveStatus(request.getIsVisible(), null))
                .build();
        product.setContents(createContents(product, contentRequests, files));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO updateProduct(
            User currentUser,
            UUID productId,
            UpdateProductRequest request,
            MultipartFile thumbnail,
            Map<String, MultipartFile> files) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompany(productId, company);
        String sku = request.getSku().trim();
        if (productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), sku, productId)) {
            throw new AppException(ErrorCode.PRODUCT_SKU_DUPLICATED);
        }

        ProductCategory category = getCategoryForUpdate(request.getCategoryId(), company, product);
        List<UUID> existingContentIds = request.getExistingContentIds() == null ? List.of() : request.getExistingContentIds();
        List<CreateProductContentRequest> newContentRequests = safeCreateContents(request.getNewContents());
        validateContentCount(existingContentIds.size() + newContentRequests.size());
        validateFileMap(newContentRequests, files);

        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateThumbnail(thumbnail);
            CloudinaryResponse thumbnailUpload = cloudService.upload(thumbnail);
            deleteCloudFile(product.getThumbnailPublicId(), "image");
            product.setThumbnailUrl(thumbnailUpload.getUrl());
            product.setThumbnailPublicId(thumbnailUpload.getPublicId());
        }

        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setSku(sku);
        product.setDescription(request.getDescription().trim());
        product.setPrice(request.getPrice());
        product.setCurrency(normalizeCurrency(request.getCurrency()));
        product.setIsVisible(resolveIsVisible(request.getIsVisible()));
        product.setStatus(resolveStatus(request.getIsVisible(), request.getStatus()));
        synchronizeContents(product, existingContentIds, newContentRequests, files);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO deleteProduct(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompany(productId, company);
        deleteCloudFile(product.getThumbnailPublicId(), "image");
        product.getContents().forEach(content ->
                deleteCloudFile(content.getPublicId(), toResourceType(content.getType())));
        product.setStatus(ProductStatus.ARCHIVED);
        product.setIsVisible(false);
        return productMapper.toResponse(productRepository.save(product));
    }

    private void synchronizeContents(
            Product product,
            List<UUID> existingContentIds,
            List<CreateProductContentRequest> newContentRequests,
            Map<String, MultipartFile> files) {
        Map<UUID, ProductContent> currentContentsById = product.getContents().stream()
                .collect(Collectors.toMap(ProductContent::getId, Function.identity()));
        List<ProductContent> nextContents = new ArrayList<>();
        Set<UUID> keptContentIds = new HashSet<>(existingContentIds);

        for (UUID contentId : existingContentIds) {
            ProductContent content = currentContentsById.get(contentId);
            if (content == null) {
                throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
            }
            nextContents.add(content);
        }

        product.getContents().stream()
                .filter(content -> !keptContentIds.contains(content.getId()))
                .forEach(content -> deleteCloudFile(content.getPublicId(), toResourceType(content.getType())));

        nextContents.addAll(createContents(product, newContentRequests, files));
        for (int i = 0; i < nextContents.size(); i++) {
            ProductContent content = nextContents.get(i);
            content.setProduct(product);
            content.setOrderIndex(i);
        }

        product.getContents().clear();
        product.getContents().addAll(nextContents);
    }

    private List<ProductContent> createContents(
            Product product,
            List<CreateProductContentRequest> contentRequests,
            Map<String, MultipartFile> files) {
        Map<String, MultipartFile> safeFiles = files == null ? Map.of() : files;
        List<ProductContent> contents = contentRequests.stream()
                .sorted(Comparator.comparing(CreateProductContentRequest::getOrderIndex))
                .map(request -> createContent(product, safeFiles.get(request.getFileKey())))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).setOrderIndex(i);
        }
        return contents;
    }

    private ProductContent createContent(Product product, MultipartFile file) {
        validateContentFile(file);
        CloudinaryResponse upload = cloudService.upload(file);
        return ProductContent.builder()
                .product(product)
                .contentUrl(upload.getUrl())
                .publicId(upload.getPublicId())
                .type(resolveContentType(upload.getFileType()))
                .orderIndex(0)
                .mimeType(upload.getFileType())
                .fileSize(upload.getFileSize())
                .build();
    }

    private ProductCategory getActiveCategoryForCompany(UUID categoryId, Company company) {
        ProductCategory category = productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
        if (category.getStatus() != ProductCategoryStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS);
        }
        return category;
    }

    private ProductCategory getCategoryForUpdate(UUID categoryId, Company company, Product product) {
        ProductCategory category = productCategoryRepository.findByIdAndCompanyId(categoryId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
        boolean keepingCurrentCategory = product.getCategory() != null
                && product.getCategory().getId() != null
                && product.getCategory().getId().equals(categoryId);
        if (!keepingCurrentCategory && category.getStatus() != ProductCategoryStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS);
        }
        return category;
    }

    private Product getProductForCompany(UUID productId, Company company) {
        return productRepository.findByIdAndCompanyId(productId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void validateFileMap(List<CreateProductContentRequest> contentRequests, Map<String, MultipartFile> files) {
        Set<String> expectedFileKeys = contentRequests.stream()
                .map(CreateProductContentRequest::getFileKey)
                .collect(Collectors.toCollection(HashSet::new));
        if (expectedFileKeys.size() != contentRequests.size()) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
        }
        Set<String> actualFileKeys = files == null ? Set.of() : files.keySet();
        if (!actualFileKeys.equals(expectedFileKeys)) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
        }
    }

    private void validateContentCount(int contentCount) {
        if (contentCount > MAX_CONTENT_COUNT) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
        }
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty() || !ALLOWED_THUMBNAIL_TYPES.contains(normalizeMimeType(thumbnail))) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
        }
    }

    private void validateContentFile(MultipartFile file) {
        if (file == null || file.isEmpty() || !ALLOWED_CONTENT_TYPES.contains(normalizeMimeType(file))) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
        }
    }

    private ProductContentType resolveContentType(String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return ProductContentType.VIDEO;
        }
        return ProductContentType.IMAGE;
    }

    private String toResourceType(ProductContentType type) {
        return type == ProductContentType.VIDEO ? "video" : "image";
    }

    private void deleteCloudFile(String publicId, String resourceType) {
        cloudService.delete(publicId, resourceType);
    }

    private String normalizeMimeType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "VND" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private Boolean resolveIsVisible(Boolean isVisible) {
        return !Boolean.FALSE.equals(isVisible);
    }

    private ProductStatus resolveStatus(Boolean isVisible, ProductStatus requestedStatus) {
        if (requestedStatus != null && requestedStatus != ProductStatus.ARCHIVED) {
            return requestedStatus;
        }
        return Boolean.FALSE.equals(isVisible) ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
    }

    private List<CreateProductContentRequest> safeCreateContents(List<CreateProductContentRequest> contents) {
        return contents == null ? List.of() : contents;
    }
}
