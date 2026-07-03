package com.example.vex360.features.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.dtos.request.CreateProductContentPreUploadedRequest;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {

        @Mock
        private CompanyService companyService;
        @Mock
        private ProductCategoryRepository productCategoryRepository;
        @Mock
        private ProductRepository productRepository;
        @Mock
        private CloudService cloudService;

        private ProductService productService;
        private User user;
        private Company company;
        private ProductCategory category;

        @BeforeEach
        void setup() {
                productService = new ProductService(
                                companyService,
                                productCategoryRepository,
                                productRepository,
                                cloudService,
                                new ProductMapper());
                user = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
                company = Company.builder().id(UUID.randomUUID()).ownerUser(user).name("Orion").build();
                category = ProductCategory.builder()
                                .id(UUID.randomUUID()).company(company).name("Máy tính")
                                .status(ProductCategoryStatus.ACTIVE).build();
        }

        @Test
        void createProductRejectsInactiveCategory() {
                category.setStatus(ProductCategoryStatus.INACTIVE);
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));

                AppException ex = assertThrows(AppException.class,
                                () -> productService.createProduct(user, validCreateRequest()));

                assertEquals(ErrorCode.INVALID_PRODUCT_CATEGORY_STATUS, ex.getErrorCode());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void createProductSavesProductWithPreUploadedUrls() {
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                        Product p = inv.getArgument(0);
                        p.setId(UUID.randomUUID());
                        p.getContents().forEach(c -> c.setId(UUID.randomUUID()));
                        return p;
                });

                productService.createProduct(user, validCreateRequest());

                ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
                verify(productRepository).save(captor.capture());
                Product saved = captor.getValue();
                assertEquals("http://cdn/thumb.png", saved.getThumbnailUrl());
                assertEquals("thumb-public-id", saved.getThumbnailPublicId());
                assertEquals(2, saved.getContents().size());
                assertEquals("pub-1", saved.getContents().get(0).getPublicId());
                assertEquals(ProductContentType.IMAGE, saved.getContents().get(0).getType());
                assertEquals("pub-2", saved.getContents().get(1).getPublicId());
                assertEquals(ProductContentType.VIDEO, saved.getContents().get(1).getType());
        }

        @Test
        void createProductUsesRequestedStatus() {
                CreateProductRequest req = validCreateRequest();
                req.setStatus(ProductStatus.INACTIVE);
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

                productService.createProduct(user, req);

                ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
                verify(productRepository).save(captor.capture());
                assertEquals(ProductStatus.INACTIVE, captor.getValue().getStatus());
        }

        @Test
        void createProductRejectsMoreThanFiveImages() {
                CreateProductRequest req = validCreateRequest();
                req.setContents(List.of(
                                content("http://cdn/1.png", "p1", "image/png", 0),
                                content("http://cdn/2.png", "p2", "image/png", 1),
                                content("http://cdn/3.png", "p3", "image/png", 2),
                                content("http://cdn/4.png", "p4", "image/png", 3),
                                content("http://cdn/5.png", "p5", "image/png", 4),
                                content("http://cdn/6.png", "p6", "image/png", 5)));
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));

                AppException ex = assertThrows(AppException.class,
                                () -> productService.createProduct(user, req));

                assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void createProductRejectsMoreThanOneVideo() {
                CreateProductRequest req = validCreateRequest();
                req.setContents(List.of(
                                content("http://cdn/img.png", "p1", "image/png", 0),
                                content("http://cdn/v1.mp4", "p2", "video/mp4", 1),
                                content("http://cdn/v2.mp4", "p3", "video/mp4", 2)));
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.existsByCompanyIdAndSkuIgnoreCase(company.getId(), "VEX-001")).thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));

                AppException ex = assertThrows(AppException.class,
                                () -> productService.createProduct(user, req));

                assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void getProductsFiltersByNameCategoryAndStatus() {
                Product product = Product.builder()
                                .id(UUID.randomUUID()).company(company).category(category)
                                .name("Robot Arm").sku("VEX-001").description("desc")
                                .price(BigDecimal.TEN).currency("VND").thumbnailUrl("/t.png")
                                .thumbnailPublicId("t-id").status(ProductStatus.ACTIVE)
                                .contents(new java.util.ArrayList<>()).build();
                PageRequest pageable = PageRequest.of(0, 10);
                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.searchProducts(company.getId(), "Robot", category.getId(), ProductStatus.ACTIVE,
                                pageable))
                                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

                PageResponse<ProductResponseDTO> response = productService.getProducts(
                                user, " Robot ", category.getId(), ProductStatus.ACTIVE, pageable);

                assertEquals(1, response.getContent().size());
                assertEquals("Robot Arm", response.getContent().get(0).getName());
        }

        @Test
        void updateProductSynchronizesExistingAndNewContents() {
                UUID productId = UUID.randomUUID();
                ProductContent removed = buildContent("old-a-public-id", "/old-a.png", "image/png",
                                ProductContentType.IMAGE, 0);
                ProductContent kept = buildContent("old-b-public-id", "/old-b.png", "image/png",
                                ProductContentType.IMAGE, 1);
                Product product = buildProduct(productId, List.of(removed, kept));
                removed.setProduct(product);
                kept.setProduct(product);

                UpdateProductRequest req = new UpdateProductRequest(
                                "Robot", "VEX-002", category.getId(), "New desc", BigDecimal.TEN, "VND",
                                ProductStatus.ACTIVE, null, null,
                                List.of(kept.getId()),
                                List.of(content("http://cdn/new.png", "new-public-id", "image/png", 1)));

                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.findByIdAndCompanyId(productId, company.getId()))
                                .thenReturn(Optional.of(product));
                when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                                .thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

                productService.updateProduct(user, productId, req);

                assertEquals(2, product.getContents().size());
                assertEquals(kept.getId(), product.getContents().get(0).getId());
                assertEquals("http://cdn/new.png", product.getContents().get(1).getContentUrl());
                assertEquals(1, product.getContents().get(1).getOrderIndex());
                verify(cloudService).delete("old-a-public-id", "image");
        }

        @Test
        void updateProductRejectsMoreThanOneVideoIncludingExistingContents() {
                UUID productId = UUID.randomUUID();
                ProductContent existingVideo = buildContent("old-vid-id", "/old.mp4", "video/mp4",
                                ProductContentType.VIDEO, 0);
                Product product = buildProduct(productId, List.of(existingVideo));
                existingVideo.setProduct(product);

                UpdateProductRequest req = new UpdateProductRequest(
                                "Robot", "VEX-002", category.getId(), "desc", BigDecimal.TEN, "VND",
                                ProductStatus.ACTIVE, null, null,
                                List.of(existingVideo.getId()),
                                List.of(content("http://cdn/new.mp4", "new-vid-id", "video/mp4", 1)));

                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.findByIdAndCompanyId(productId, company.getId()))
                                .thenReturn(Optional.of(product));
                when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                                .thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));

                AppException ex = assertThrows(AppException.class,
                                () -> productService.updateProduct(user, productId, req));

                assertEquals(ErrorCode.INVALID_PRODUCT_MEDIA, ex.getErrorCode());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void updateProductReplacingThumbnailDeletesOldFromCloudinary() {
                UUID productId = UUID.randomUUID();
                Product product = buildProduct(productId, new java.util.ArrayList<>());

                UpdateProductRequest req = new UpdateProductRequest(
                                "Robot", "VEX-002", category.getId(), "desc", BigDecimal.TEN, "VND",
                                ProductStatus.ACTIVE, "http://cdn/new-thumb.png", "new-thumb-id",
                                List.of(), List.of());

                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.findByIdAndCompanyId(productId, company.getId()))
                                .thenReturn(Optional.of(product));
                when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                                .thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

                productService.updateProduct(user, productId, req);

                assertEquals("http://cdn/new-thumb.png", product.getThumbnailUrl());
                assertEquals("new-thumb-id", product.getThumbnailPublicId());
                verify(cloudService).delete("old-thumb-public-id", "image");
        }

        @Test
        void deleteProductDeletesCloudFilesAndArchives() {
                UUID productId = UUID.randomUUID();
                ProductContent img = buildContent("img-id", "/img.png", "image/png", ProductContentType.IMAGE, 0);
                ProductContent vid = buildContent("vid-id", "/vid.mp4", "video/mp4", ProductContentType.VIDEO, 1);
                Product product = buildProduct(productId, new java.util.ArrayList<>(List.of(img, vid)));
                img.setProduct(product);
                vid.setProduct(product);

                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.findByIdAndCompanyId(productId, company.getId()))
                                .thenReturn(Optional.of(product));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

                productService.deleteProduct(user, productId);

                verify(cloudService).delete("old-thumb-public-id", "image");
                verify(cloudService).delete("img-id", "image");
                verify(cloudService).delete("vid-id", "video");
                assertEquals(ProductStatus.INACTIVE, product.getStatus());
        }

        @Test
        void updateProductAllowsKeepingCurrentInactiveCategory() {
                UUID productId = UUID.randomUUID();
                category.setStatus(ProductCategoryStatus.INACTIVE);
                Product product = buildProduct(productId, new java.util.ArrayList<>());

                UpdateProductRequest req = new UpdateProductRequest(
                                "Robot", "VEX-002", category.getId(), "desc", BigDecimal.TEN, "VND",
                                ProductStatus.ACTIVE, null, null, List.of(), List.of());

                when(companyService.getCompanyEntityForCurrentUser(user)).thenReturn(company);
                when(productRepository.findByIdAndCompanyId(productId, company.getId()))
                                .thenReturn(Optional.of(product));
                when(productRepository.existsByCompanyIdAndSkuIgnoreCaseAndIdNot(company.getId(), "VEX-002", productId))
                                .thenReturn(false);
                when(productCategoryRepository.findByIdAndCompanyId(category.getId(), company.getId()))
                                .thenReturn(Optional.of(category));
                when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

                productService.updateProduct(user, productId, req);

                assertEquals(category, product.getCategory());
                assertEquals(ProductStatus.INACTIVE, product.getStatus());
        }

        // ─── Helpers ─────────────────────────────────────────────────────────────

        private CreateProductRequest validCreateRequest() {
                return new CreateProductRequest(
                                "Robot", "VEX-001", category.getId(), "Robot demo",
                                BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                                "http://cdn/thumb.png", "thumb-public-id",
                                List.of(
                                                content("http://cdn/img.png", "pub-1", "image/png", 0),
                                                content("http://cdn/video.mp4", "pub-2", "video/mp4", 1)));
        }

        private CreateProductContentPreUploadedRequest content(String url, String publicId, String mime, int idx) {
                return new CreateProductContentPreUploadedRequest(url, publicId, mime, 1000L, idx);
        }

        private ProductContent buildContent(String publicId, String url, String mime, ProductContentType type,
                        int idx) {
                return ProductContent.builder()
                                .id(UUID.randomUUID()).contentUrl(url).publicId(publicId)
                                .type(type).orderIndex(idx).mimeType(mime).fileSize(100L).build();
        }

        private Product buildProduct(UUID id, java.util.List<ProductContent> contents) {
                return Product.builder()
                                .id(id).company(company).category(category)
                                .name("Old").sku("OLD").description("Old desc")
                                .price(BigDecimal.ONE).currency("VND")
                                .thumbnailUrl("/old-thumb.png").thumbnailPublicId("old-thumb-public-id")
                                .status(ProductStatus.ACTIVE).contents(contents).build();
        }
}
