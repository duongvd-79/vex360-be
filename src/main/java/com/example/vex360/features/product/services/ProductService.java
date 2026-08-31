package com.example.vex360.features.product.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import com.example.vex360.shared.enums.StorageProvider;
import com.example.vex360.shared.services.R2StorageService;
import com.example.vex360.shared.utils.PageableUtils;

@Service
public class ProductService {
    private static final int MAX_IMAGE_CONTENT_COUNT = 5;
    private static final int MAX_VIDEO_CONTENT_COUNT = 1;
    private static final int MAX_MODEL_CONTENT_COUNT = 1;
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
    private final R2StorageService r2StorageService;

    public ProductService(
            CompanyService companyService,
            CompanyStorageService companyStorageService,
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            CloudService cloudService,
            ProductMapper productMapper,
            ApplicationEventPublisher eventPublisher,
            R2StorageService r2StorageService) {
        this.companyService = companyService;
        this.companyStorageService = companyStorageService;
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.cloudService = cloudService;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
        this.r2StorageService = r2StorageService;
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
        Map<String, Long> verifiedSizes = measureR2Contents(company, contentRequests);
        chargeR2Usage(company, verifiedSizes);
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
        product.setContents(createContentsFromUploaded(product, contentRequests, verifiedSizes));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO updateProduct(
            User currentUser,
            UUID productId,
            UpdateProductRequest request) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompanyForUpdate(productId, company);
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

        Map<String, Long> verifiedSizes = measureR2Contents(company, newContentRequests);
        chargeR2Usage(company, verifiedSizes);
        synchronizeContents(product, existingContentIds,
                createContentsFromUploaded(product, newContentRequests, verifiedSizes));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO deleteProduct(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompanyForUpdate(productId, company);
        assertNotUsedByPendingBooth(productId);
        companyStorageService.deductUsage(company, product.getThumbnailFileSize());
        deleteCloudFile(product.getThumbnailPublicId(), "image");
        product.getContents().forEach(content -> {
            companyStorageService.deductUsage(company, content.getFileSize());
            deleteContentFile(content);
        });
        eventPublisher.publishEvent(new ProductDeletedEvent(this, product));
        product.setStatus(ProductStatus.INACTIVE);
        return productMapper.toResponse(productRepository.save(product));
    }

    /**
     * Hỏi thẳng R2 xem từng tệp cửa vào có thật không và thư mục chứa nó nặng bao nhiêu.
     * Trả về bảng tra khoá đến dung lượng thật, dùng thay cho con số do client khai.
     */
    private Map<String, Long> measureR2Contents(
            Company company,
            List<CreateProductContentPreUploadedRequest> requests) {
        Map<String, Long> verifiedSizes = new HashMap<>();
        Set<String> measuredFolders = new HashSet<>();
        for (CreateProductContentPreUploadedRequest req : requests) {
            if (req.getStorageProvider() != StorageProvider.R2) {
                continue;
            }
            // Các tệp của một lô presign nằm chung thư mục; chỉ đo và tính phí thư mục một
            // lần, những tệp cùng thư mục mang dung lượng 0 để tổng không bị nhân lên.
            String folder = req.getPublicId().substring(0, req.getPublicId().lastIndexOf('/') + 1);
            long size = measuredFolders.add(folder)
                    ? r2StorageService.verifyAndMeasureFolder(company.getId(), req.getPublicId())
                    : 0L;
            verifiedSizes.put(req.getPublicId(), size);
        }
        return verifiedSizes;
    }

    /**
     * Trừ kho lưu trữ cho phần nằm trên R2. Tệp Cloudinary không tính lại ở đây vì đã bị trừ
     * ngay lúc tải lên, còn tệp R2 đi thẳng từ trình duyệt nên tới bước này mới chốt được.
     */
    private void chargeR2Usage(Company company, Map<String, Long> verifiedSizes) {
        long totalBytes = verifiedSizes.values().stream().mapToLong(Long::longValue).sum();
        if (totalBytes <= 0) {
            return;
        }
        companyStorageService.checkQuota(company, totalBytes);
        companyStorageService.addUsage(company, totalBytes);
    }

    private List<ProductContent> createContentsFromUploaded(
            Product product,
            List<CreateProductContentPreUploadedRequest> requests,
            Map<String, Long> verifiedSizes) {
        List<ProductContent> contents = requests.stream()
                .sorted(Comparator.comparing(CreateProductContentPreUploadedRequest::getOrderIndex))
                .map(req -> createContentFromUploaded(product, req, verifiedSizes))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).setOrderIndex(i);
        }
        return contents;
    }

    private ProductContent createContentFromUploaded(
            Product product,
            CreateProductContentPreUploadedRequest req,
            Map<String, Long> verifiedSizes) {
        StorageProvider provider = req.getStorageProvider() == null
                ? StorageProvider.CLOUDINARY
                : req.getStorageProvider();
        return ProductContent.builder()
                .product(product)
                .contentUrl(req.getContentUrl())
                .publicId(req.getPublicId())
                .storageProvider(provider)
                .type(resolveContentType(req.getMimeType()))
                .orderIndex(req.getOrderIndex())
                .mimeType(req.getMimeType())
                // Dung lượng đo được trên R2 thắng con số client khai; tệp Cloudinary thì
                // không có gì để đối chiếu nên giữ nguyên.
                .fileSize(verifiedSizes.getOrDefault(req.getPublicId(), req.getFileSize()))
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
                .forEach(content -> deleteContentFile(content));

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
        validateContentTypeCounts(
                countOf(requests, ProductContentType.IMAGE),
                countOf(requests, ProductContentType.VIDEO),
                countOf(requests, ProductContentType.MODEL_3D));
    }

    private void validateContentTypeCountsForUpdate(
            Product product,
            List<UUID> existingContentIds,
            List<CreateProductContentPreUploadedRequest> newRequests) {
        Set<UUID> keptIds = new HashSet<>(existingContentIds);
        validateContentTypeCounts(
                countOf(product, keptIds, ProductContentType.IMAGE)
                        + countOf(newRequests, ProductContentType.IMAGE),
                countOf(product, keptIds, ProductContentType.VIDEO)
                        + countOf(newRequests, ProductContentType.VIDEO),
                countOf(product, keptIds, ProductContentType.MODEL_3D)
                        + countOf(newRequests, ProductContentType.MODEL_3D));
    }

    /** Đếm nội dung sắp thêm, phân loại theo kiểu suy ra từ MIME type. */
    private long countOf(List<CreateProductContentPreUploadedRequest> requests, ProductContentType type) {
        return requests.stream()
                .map(r -> resolveContentType(r.getMimeType()))
                .filter(type::equals).count();
    }

    /** Đếm nội dung cũ được giữ lại sau khi cập nhật. */
    private long countOf(Product product, Set<UUID> keptIds, ProductContentType type) {
        return product.getContents().stream()
                .filter(c -> keptIds.contains(c.getId()))
                .map(ProductContent::getType)
                .filter(type::equals).count();
    }

    private void validateContentTypeCounts(long imageCount, long videoCount, long modelCount) {
        if (imageCount > MAX_IMAGE_CONTENT_COUNT
                || videoCount > MAX_VIDEO_CONTENT_COUNT
                || modelCount > MAX_MODEL_CONTENT_COUNT) {
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

    /**
     * Xoá tệp của một nội dung ở đúng kho đang giữ nó. Đưa khoá R2 cho Cloudinary thì
     * Cloudinary không tìm thấy, không báo lỗi, và tệp nằm lại vĩnh viễn kèm dung lượng
     * không bao giờ được trả về cho doanh nghiệp.
     */
    private void deleteContentFile(ProductContent content) {
        if (content.resolveStorageProvider() == StorageProvider.R2) {
            r2StorageService.deleteFolderOf(content.getPublicId());
            return;
        }
        deleteCloudFile(content.getPublicId(), toResourceType(content.getType()));
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
        String normalized = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("video/")) {
            return ProductContentType.VIDEO;
        }
        // Mô hình 3D luôn được ký với model/obj, model/mtl hoặc model/gltf-binary nên một
        // tiền tố là đủ, không phải liệt kê từng phần mở rộng.
        if (normalized.startsWith("model/")) {
            return ProductContentType.MODEL_3D;
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

    private Product getProductForCompanyForUpdate(UUID productId, Company company) {
        return productRepository.findByIdAndCompanyIdForUpdate(productId, company.getId())
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
