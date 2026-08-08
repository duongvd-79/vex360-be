package com.example.vex360.features.product.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.CreateProductContentPreUploadedRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.events.ProductDeletedEvent;
import com.example.vex360.features.product.mapper.ProductMapper;
import com.example.vex360.features.product.repositories.ProductCategoryRepository;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductCategory;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.PageableUtils;

@Service
public class ProductService {
    private static final int MAX_IMAGE_CONTENT_COUNT = 5;
    private static final int MAX_VIDEO_CONTENT_COUNT = 1;
    private static final List<String> BOOTH_STATUSES_LOCKING_PRODUCT_EDITS = List.of("PENDING", "PUBLISHED");
    private static final Map<String, String> PRODUCT_SORT_ALIASES = Map.of(
            "categoryName", "category.name");

    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final CloudService cloudService;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(
            CompanyService companyService,
            CompanyStorageService companyStorageService,
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            CloudService cloudService,
            ProductMapper productMapper,
            ApplicationEventPublisher eventPublisher) {
        this.companyService = companyService;
        this.companyStorageService = companyStorageService;
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.cloudService = cloudService;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getProducts(
            User currentUser,
            String keyword,
            UUID categoryId,
            ProductStatus status,
            Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Pageable mappedPageable = PageableUtils.remapSort(pageable, PRODUCT_SORT_ALIASES);
        Page<ProductResponseDTO> products = productRepository
                .searchProducts(company.getId(), normalizeKeyword(keyword), categoryId, status, mappedPageable)
                .map(productMapper::toResponse);
        return PageResponse.from(products);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getActiveProductsForCompany(
            UUID companyId,
            String keyword,
            UUID categoryId,
            Pageable pageable) {
        return PageResponse.from(productRepository
                .searchProducts(companyId, normalizeKeyword(keyword), categoryId, ProductStatus.ACTIVE, pageable)
                .map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return productMapper.toResponse(getProductForCompany(productId, company));
    }

    @Transactional
    public ProductResponseDTO createProduct(
            User currentUser,
            CreateProductRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        String sku = request.getSku().trim();
        if (productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), sku)) {
            throw new AppException(ErrorCode.PRODUCT_SKU_DUPLICATED);
        }

        ProductCategory category = getActiveCategoryForCompany(request.getCategoryId(), company);
        List<CreateProductContentPreUploadedRequest> contentRequests = request.getContents() == null ? List.of()
                : request.getContents();
        validateContentTypeCounts(contentRequests);
        ProductStatus status = resolveMutableStatus(request.getStatus());

        Product product = Product.builder()
                .company(company)
                .category(category)
                .name(request.getName().trim())
                .sku(sku)
                .description(request.getDescription().trim())
                .price(request.getPrice())
                .currency(normalizeCurrency(request.getCurrency()))
                .thumbnailUrl(request.getThumbnailUrl())
                .thumbnailPublicId(request.getThumbnailPublicId())
                .thumbnailFileSize(request.getThumbnailFileSize())
                .status(status)
                .build();
        product.setContents(createContentsFromUploaded(product, contentRequests));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO updateProduct(
            User currentUser,
            UUID productId,
            UpdateProductRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompany(productId, company);
        assertNotUsedByPendingBooth(productId);
        String sku = request.getSku().trim();
        if (productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), sku, productId)) {
            throw new AppException(ErrorCode.PRODUCT_SKU_DUPLICATED);
        }

        ProductCategory category = getCategoryForUpdate(request.getCategoryId(), company, product);
        List<UUID> existingContentIds = request.getExistingContentIds() == null ? List.of()
                : request.getExistingContentIds();
        List<CreateProductContentPreUploadedRequest> newContentRequests = request.getNewContents() == null ? List.of()
                : request.getNewContents();
        validateExistingContentIds(product, existingContentIds);
        validateContentTypeCountsForUpdate(product, existingContentIds, newContentRequests);
        ProductStatus status = resolveStatusForCategory(category, request.getStatus());

        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setSku(sku);
        product.setDescription(request.getDescription().trim());
        product.setPrice(request.getPrice());
        product.setCurrency(normalizeCurrency(request.getCurrency()));
        product.setStatus(status);
        if (request.getThumbnailUrl() != null && !request.getThumbnailUrl().isBlank()) {
            companyStorageService.deductUsage(company, product.getThumbnailFileSize());
            deleteCloudFile(product.getThumbnailPublicId(), "image");
            product.setThumbnailUrl(request.getThumbnailUrl());
            product.setThumbnailPublicId(request.getThumbnailPublicId());
            product.setThumbnailFileSize(request.getThumbnailFileSize() != null ? request.getThumbnailFileSize() : 0L);
        }

        Set<UUID> keptIds = new HashSet<>(existingContentIds);
        product.getContents().stream()
                .filter(content -> !keptIds.contains(content.getId()))
                .forEach(content -> companyStorageService.deductUsage(company, content.getFileSize()));

        synchronizeContents(product, existingContentIds, createContentsFromUploaded(product, newContentRequests));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO deleteProduct(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompany(productId, company);
        assertNotUsedByPendingBooth(productId);
        companyStorageService.deductUsage(company, product.getThumbnailFileSize());
        deleteCloudFile(product.getThumbnailPublicId(), "image");
        product.getContents().forEach(content -> {
            companyStorageService.deductUsage(company, content.getFileSize());
            deleteCloudFile(content.getPublicId(), toResourceType(content.getType()));
        });
        eventPublisher.publishEvent(new ProductDeletedEvent(this, product));
        product.setStatus(ProductStatus.INACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }

    private List<ProductContent> createContentsFromUploaded(
            Product product,
            List<CreateProductContentPreUploadedRequest> requests) {
        List<ProductContent> contents = requests.stream()
                .sorted(Comparator.comparing(CreateProductContentPreUploadedRequest::getOrderIndex))
                .map(req -> createContentFromUploaded(product, req))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).setOrderIndex(i);
        }
        return contents;
    }

    private ProductContent createContentFromUploaded(Product product, CreateProductContentPreUploadedRequest req) {
        return ProductContent.builder()
                .product(product)
                .contentUrl(req.getContentUrl())
                .publicId(req.getPublicId())
                .type(resolveContentType(req.getMimeType()))
                .orderIndex(req.getOrderIndex())
                .mimeType(req.getMimeType())
                .fileSize(req.getFileSize())
                .build();
    }

    private void synchronizeContents(
            Product product,
            List<UUID> existingContentIds,
            List<ProductContent> newContents) {
        Map<UUID, ProductContent> currentContentsById = product.getContents().stream()
                .collect(Collectors.toMap(ProductContent::getId, Function.identity()));
        List<ProductContent> nextContents = new ArrayList<>();
        Set<UUID> keptContentIds = new HashSet<>(existingContentIds);

        for (UUID contentId : existingContentIds) {
            ProductContent content = currentContentsById.get(contentId);
            if (content == null)
                throw new AppException(ErrorCode.PRODUCT_MEDIA_REFERENCE_INVALID);
            nextContents.add(content);
        }

        product.getContents().stream()
                .filter(content -> !keptContentIds.contains(content.getId()))
                .forEach(content -> deleteCloudFile(content.getPublicId(), toResourceType(content.getType())));

        nextContents.addAll(newContents);
        for (int i = 0; i < nextContents.size(); i++) {
            ProductContent content = nextContents.get(i);
            content.setProduct(product);
            content.setOrderIndex(i);
        }

        product.getContents().clear();
        product.getContents().addAll(nextContents);
    }

    private void validateContentTypeCounts(List<CreateProductContentPreUploadedRequest> requests) {
        long imageCount = requests.stream()
                .map(r -> resolveContentType(r.getMimeType()))
                .filter(ProductContentType.IMAGE::equals).count();
        long videoCount = requests.stream()
                .map(r -> resolveContentType(r.getMimeType()))
                .filter(ProductContentType.VIDEO::equals).count();
        validateContentTypeCounts(imageCount, videoCount);
    }

    private void validateContentTypeCountsForUpdate(
            Product product,
            List<UUID> existingContentIds,
            List<CreateProductContentPreUploadedRequest> newRequests) {
        Set<UUID> keptIds = new HashSet<>(existingContentIds);
        long imageCount = product.getContents().stream()
                .filter(c -> keptIds.contains(c.getId()))
                .map(ProductContent::getType)
                .filter(ProductContentType.IMAGE::equals).count();
        long videoCount = product.getContents().stream()
                .filter(c -> keptIds.contains(c.getId()))
                .map(ProductContent::getType)
                .filter(ProductContentType.VIDEO::equals).count();
        imageCount += newRequests.stream()
                .map(r -> resolveContentType(r.getMimeType()))
                .filter(ProductContentType.IMAGE::equals).count();
        videoCount += newRequests.stream()
                .map(r -> resolveContentType(r.getMimeType()))
                .filter(ProductContentType.VIDEO::equals).count();
        validateContentTypeCounts(imageCount, videoCount);
    }

    private void validateContentTypeCounts(long imageCount, long videoCount) {
        if (imageCount > MAX_IMAGE_CONTENT_COUNT || videoCount > MAX_VIDEO_CONTENT_COUNT) {
            throw new AppException(ErrorCode.PRODUCT_MEDIA_LIMIT_EXCEEDED);
        }
    }

    private void validateExistingContentIds(Product product, List<UUID> existingContentIds) {
        Set<UUID> currentContentIds = product.getContents().stream()
                .map(ProductContent::getId)
                .collect(Collectors.toCollection(HashSet::new));
        for (UUID contentId : existingContentIds) {
            if (!currentContentIds.contains(contentId)) {
                throw new AppException(ErrorCode.PRODUCT_MEDIA_REFERENCE_INVALID);
            }
        }
    }

    private void deleteCloudFile(String publicId, String resourceType) {
        cloudService.delete(publicId, resourceType);
    }

    private void assertNotUsedByPendingBooth(UUID productId) {
        Long lockedByDesignRequest = productRepository.existsLockedByDesignRequest(productId);
        if (lockedByDesignRequest != null && lockedByDesignRequest > 0) {
            throw new AppException(ErrorCode.DESIGN_PRODUCT_LOCKED);
        }
        Long usedByBooth = productRepository.existsInBoothWithStatus(productId, BOOTH_STATUSES_LOCKING_PRODUCT_EDITS);
        if (usedByBooth != null && usedByBooth > 0) {
            throw new AppException(ErrorCode.PRODUCT_USED_BY_PENDING_BOOTH);
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

    @Transactional(readOnly = true)
    public Product getProductForCompany(UUID productId, Company company) {
        return productRepository.findByIdAndCompanyId(productId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "VND" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private ProductStatus resolveMutableStatus(ProductStatus requestedStatus) {
        if (requestedStatus == ProductStatus.ACTIVE || requestedStatus == ProductStatus.INACTIVE) {
            return requestedStatus;
        }
        throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
    }

    private ProductStatus resolveStatusForCategory(ProductCategory category, ProductStatus requestedStatus) {
        ProductStatus status = resolveMutableStatus(requestedStatus);
        if (category.getStatus() == ProductCategoryStatus.INACTIVE) {
            return ProductStatus.INACTIVE;
        }
        return status;
    }

    @Transactional(readOnly = true)
    public Optional<Product> findOptionalProductById(UUID productId) {
        return productRepository.findById(productId);
    }

    @Transactional(readOnly = true)
    public List<Product> findProductsByIdsAndCompanyId(Collection<UUID> ids, UUID companyId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findByIdInAndCompanyId(new ArrayList<>(ids), companyId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProductResponseDTO> findActiveProductResponsesByIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllDetailsByIdInAndStatus(productIds, ProductStatus.ACTIVE)
                .stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toMap(ProductResponseDTO::getId, p -> p));
    }

    @Transactional(readOnly = true)
    public boolean isAssetReferenced(String publicId) {
        return productRepository.existsByThumbnailPublicId(publicId)
                || productRepository.existsContentByPublicId(publicId);
    }
}
