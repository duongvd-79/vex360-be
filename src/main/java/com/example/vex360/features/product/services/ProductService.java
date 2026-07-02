package com.example.vex360.features.product.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
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

@Service
public class ProductService {
    private static final int MAX_IMAGE_CONTENT_COUNT = 5;
    private static final int MAX_VIDEO_CONTENT_COUNT = 1;
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");

    private final CompanyRepository companyRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final CloudService cloudService;
    private final ProductMapper productMapper;
    private final Executor productMediaUploadExecutor;

    public ProductService(
            CompanyRepository companyRepository,
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            CloudService cloudService,
            ProductMapper productMapper,
            @Qualifier("productMediaUploadExecutor") Executor productMediaUploadExecutor) {
        this.companyRepository = companyRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.cloudService = cloudService;
        this.productMapper = productMapper;
        this.productMediaUploadExecutor = productMediaUploadExecutor;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getProducts(
            User currentUser,
            String keyword,
            UUID categoryId,
            ProductStatus status,
            Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<ProductResponseDTO> products = productRepository
                .searchProducts(company.getId(), normalizeKeyword(keyword), categoryId, status, pageable)
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
        validateFileMap(contentRequests, files);
        validateContentFiles(contentRequests, files);
        validateContentTypeCounts(contentRequests, files);
        ProductStatus status = resolveMutableStatus(request.getStatus());

        ProductMediaUploads uploads = uploadProductMedia(thumbnail, contentRequests, files);
        Product product = Product.builder()
                .company(company)
                .category(category)
                .name(request.getName().trim())
                .sku(sku)
                .description(request.getDescription().trim())
                .price(request.getPrice())
                .currency(normalizeCurrency(request.getCurrency()))
                .thumbnailUrl(uploads.thumbnailUpload().getUrl())
                .thumbnailPublicId(uploads.thumbnailUpload().getPublicId())
                .status(status)
                .build();
        product.setContents(createContents(product, uploads.contentUploads()));

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
        validateFileMap(newContentRequests, files);
        validateContentFiles(newContentRequests, files);
        validateExistingContentIds(product, existingContentIds);
        validateContentTypeCounts(product, existingContentIds, newContentRequests, files);
        ProductStatus status = resolveStatusForCategory(category, request.getStatus());

        boolean replacingThumbnail = thumbnail != null && !thumbnail.isEmpty();
        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateThumbnail(thumbnail);
        }

        ProductMediaUploads uploads = uploadProductMedia(
                replacingThumbnail ? thumbnail : null,
                newContentRequests,
                files);

        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setSku(sku);
        product.setDescription(request.getDescription().trim());
        product.setPrice(request.getPrice());
        product.setCurrency(normalizeCurrency(request.getCurrency()));
        product.setStatus(status);
        if (replacingThumbnail) {
            deleteCloudFile(product.getThumbnailPublicId(), "image");
            product.setThumbnailUrl(uploads.thumbnailUpload().getUrl());
            product.setThumbnailPublicId(uploads.thumbnailUpload().getPublicId());
        }
        synchronizeContents(product, existingContentIds, createContents(product, uploads.contentUploads()));

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDTO deleteProduct(User currentUser, UUID productId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Product product = getProductForCompany(productId, company);
        deleteCloudFile(product.getThumbnailPublicId(), "image");
        product.getContents().forEach(content ->
                deleteCloudFile(content.getPublicId(), toResourceType(content.getType())));
        product.setStatus(ProductStatus.INACTIVE);
        return productMapper.toResponse(productRepository.save(product));
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
            if (content == null) {
                throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
            }
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

    private void validateExistingContentIds(Product product, List<UUID> existingContentIds) {
        Set<UUID> currentContentIds = product.getContents().stream()
                .map(ProductContent::getId)
                .collect(Collectors.toCollection(HashSet::new));
        for (UUID contentId : existingContentIds) {
            if (!currentContentIds.contains(contentId)) {
                throw new AppException(ErrorCode.INVALID_PRODUCT_MEDIA);
            }
        }
    }

    private List<ProductContent> createContents(
            Product product,
            List<CloudinaryResponse> contentUploads) {
        List<ProductContent> contents = contentUploads.stream()
                .map(upload -> createContent(product, upload))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).setOrderIndex(i);
        }
        return contents;
    }

    private ProductContent createContent(Product product, CloudinaryResponse upload) {
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

    private ProductMediaUploads uploadProductMedia(
            MultipartFile thumbnail,
            List<CreateProductContentRequest> contentRequests,
            Map<String, MultipartFile> files) {
        Map<String, MultipartFile> safeFiles = files == null ? Map.of() : files;
        List<CloudinaryResponse> uploadedFiles = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<CloudinaryResponse> thumbnailFuture = thumbnail == null
                ? null
                : uploadAsync(thumbnail, uploadedFiles);
        List<CompletableFuture<CloudinaryResponse>> contentFutures = contentRequests.stream()
                .sorted(Comparator.comparing(CreateProductContentRequest::getOrderIndex))
                .map(request -> uploadAsync(safeFiles.get(request.getFileKey()), uploadedFiles))
                .collect(Collectors.toCollection(ArrayList::new));

        List<CompletableFuture<CloudinaryResponse>> allFutures = new ArrayList<>(contentFutures);
        if (thumbnailFuture != null) {
            allFutures.add(thumbnailFuture);
        }

        try {
            CompletableFuture.allOf(allFutures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            cleanupUploadedFiles(uploadedFiles);
            throw toAppException(exception);
        }

        CloudinaryResponse thumbnailUpload = thumbnailFuture == null ? null : thumbnailFuture.join();
        List<CloudinaryResponse> contentUploads = contentFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toCollection(ArrayList::new));
        return new ProductMediaUploads(thumbnailUpload, contentUploads);
    }

    private CompletableFuture<CloudinaryResponse> uploadAsync(
            MultipartFile file,
            List<CloudinaryResponse> uploadedFiles) {
        return CompletableFuture
                .supplyAsync(() -> cloudService.upload(file), productMediaUploadExecutor)
                .whenComplete((upload, exception) -> {
                    if (exception == null && upload != null) {
                        uploadedFiles.add(upload);
                    }
                });
    }

    private void cleanupUploadedFiles(List<CloudinaryResponse> uploadedFiles) {
        uploadedFiles.forEach(upload -> {
            try {
                deleteCloudFile(upload.getPublicId(), toResourceType(resolveContentType(upload.getFileType())));
            } catch (RuntimeException ignored) {
                // Keep the original upload failure as the response error.
            }
        });
    }

    private AppException toAppException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AppException appException) {
            return appException;
        }
        return new AppException(ErrorCode.UPLOAD_FAILED);
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

    private void validateContentFiles(
            List<CreateProductContentRequest> contentRequests,
            Map<String, MultipartFile> files) {
        Map<String, MultipartFile> safeFiles = files == null ? Map.of() : files;
        contentRequests.forEach(request -> validateContentFile(safeFiles.get(request.getFileKey())));
    }

    private void validateContentTypeCounts(
            List<CreateProductContentRequest> contentRequests,
            Map<String, MultipartFile> files) {
        Map<String, MultipartFile> safeFiles = files == null ? Map.of() : files;
        long imageCount = contentRequests.stream()
                .map(request -> resolveContentType(normalizeMimeType(safeFiles.get(request.getFileKey()))))
                .filter(ProductContentType.IMAGE::equals)
                .count();
        long videoCount = contentRequests.stream()
                .map(request -> resolveContentType(normalizeMimeType(safeFiles.get(request.getFileKey()))))
                .filter(ProductContentType.VIDEO::equals)
                .count();
        validateContentTypeCounts(imageCount, videoCount);
    }

    private void validateContentTypeCounts(
            Product product,
            List<UUID> existingContentIds,
            List<CreateProductContentRequest> newContentRequests,
            Map<String, MultipartFile> files) {
        Set<UUID> keptContentIds = new HashSet<>(existingContentIds);
        long imageCount = product.getContents().stream()
                .filter(content -> keptContentIds.contains(content.getId()))
                .map(ProductContent::getType)
                .filter(ProductContentType.IMAGE::equals)
                .count();
        long videoCount = product.getContents().stream()
                .filter(content -> keptContentIds.contains(content.getId()))
                .map(ProductContent::getType)
                .filter(ProductContentType.VIDEO::equals)
                .count();

        Map<String, MultipartFile> safeFiles = files == null ? Map.of() : files;
        imageCount += newContentRequests.stream()
                .map(request -> resolveContentType(normalizeMimeType(safeFiles.get(request.getFileKey()))))
                .filter(ProductContentType.IMAGE::equals)
                .count();
        videoCount += newContentRequests.stream()
                .map(request -> resolveContentType(normalizeMimeType(safeFiles.get(request.getFileKey()))))
                .filter(ProductContentType.VIDEO::equals)
                .count();
        validateContentTypeCounts(imageCount, videoCount);
    }

    private void validateContentTypeCounts(long imageCount, long videoCount) {
        if (imageCount > MAX_IMAGE_CONTENT_COUNT || videoCount > MAX_VIDEO_CONTENT_COUNT) {
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

    private List<CreateProductContentRequest> safeCreateContents(List<CreateProductContentRequest> contents) {
        return contents == null ? List.of() : contents;
    }

    private record ProductMediaUploads(
            CloudinaryResponse thumbnailUpload,
            List<CloudinaryResponse> contentUploads) {
    }
}
