package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestProductServiceAdditionalUnitTest {

    @Mock
    private DesignRequestProductRepository requestProductRepository;
    @Mock
    private ProductService productService;
    @Mock
    private BoothDesignService boothDesignService;

    private DesignRequestProductService service;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setUp() {
        service = new DesignRequestProductService(requestProductRepository, productService, boothDesignService);
        company = Company.builder().id(UUID.randomUUID()).build();
        booth = Booth.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void assertProductAllowedAcceptsAllowlistedProduct() {
        DesignRequest request = request(DesignRequestMode.INITIAL_DESIGN);
        UUID productId = UUID.randomUUID();
        when(requestProductRepository.existsByDesignRequestIdAndProductId(request.getId(), productId))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.assertProductAllowed(request, productId));
    }

    @Test
    void assertProductAllowedRejectsProductOutsideAllowlist() {
        DesignRequest request = request(DesignRequestMode.INITIAL_DESIGN);
        UUID productId = UUID.randomUUID();

        AppException exception = assertThrows(
                AppException.class,
                () -> service.assertProductAllowed(request, productId));

        assertSame(ErrorCode.DESIGN_PRODUCT_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void initializeAllowlistRejectsNullProductId() {
        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(
                        request(DesignRequestMode.INITIAL_DESIGN), Collections.singletonList(null)));

        assertSame(ErrorCode.INVALID_DESIGN_DRAFT, exception.getErrorCode());
    }

    @Test
    void initializeAllowlistRejectsMissingCompanyProduct() {
        DesignRequest request = request(DesignRequestMode.INITIAL_DESIGN);
        UUID productId = UUID.randomUUID();
        when(productService.findProductsByIdsAndCompanyId(List.of(productId), company.getId()))
                .thenReturn(List.of());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(request, List.of(productId)));

        assertSame(ErrorCode.INVALID_PRODUCT_STATUS, exception.getErrorCode());
    }

    @Test
    void initializeAllowlistRejectsInactiveSelectedProduct() {
        DesignRequest request = request(DesignRequestMode.INITIAL_DESIGN);
        Product inactive = product(ProductStatus.INACTIVE);
        when(productService.findProductsByIdsAndCompanyId(List.of(inactive.getId()), company.getId()))
                .thenReturn(List.of(inactive));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(request, List.of(inactive.getId())));

        assertSame(ErrorCode.INVALID_PRODUCT_STATUS, exception.getErrorCode());
    }

    @Test
    void redesignIncludesActiveBaselineProductAsRequired() {
        DesignRequest request = request(DesignRequestMode.REDESIGN);
        Product baseline = product(ProductStatus.ACTIVE);
        when(boothDesignService.findDistinctProductIdsByBoothId(booth.getId(), null))
                .thenReturn(List.of(baseline.getId()));
        when(productService.findProductsByIdsAndCompanyId(List.of(baseline.getId()), company.getId()))
                .thenReturn(List.of(baseline));

        service.initializeAllowlist(request, List.of());

        assertEquals(1, request.getProducts().size());
        assertSame(baseline, request.getProducts().get(0).getProduct());
        assertEquals(Boolean.TRUE, request.getProducts().get(0).getRequiredFromBaseline());
    }

    @Test
    void redesignRejectsInactiveBaselineProduct() {
        DesignRequest request = request(DesignRequestMode.REDESIGN);
        Product inactive = product(ProductStatus.INACTIVE);
        when(boothDesignService.findDistinctProductIdsByBoothId(booth.getId(), null))
                .thenReturn(List.of(inactive.getId()));
        when(productService.findProductsByIdsAndCompanyId(List.of(inactive.getId()), company.getId()))
                .thenReturn(List.of(inactive));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.initializeAllowlist(request, List.of()));

        assertSame(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE, exception.getErrorCode());
    }

    private DesignRequest request(DesignRequestMode mode) {
        return DesignRequest.builder()
                .id(UUID.randomUUID())
                .company(company)
                .booth(booth)
                .mode(mode)
                .build();
    }

    private Product product(ProductStatus status) {
        return Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .status(status)
                .build();
    }
}
