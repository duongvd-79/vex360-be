package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignRequestScope;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.designrequest.services.DesignRequestProductService;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DesignRequestProductServiceUnitTest {
    @Mock
    DesignRequestProductRepository requestProductRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    HotspotRepository hotspotRepository;

    @Test
    void pendingAllowlistUpdateKeepsRequiredBaselineProduct() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        Product required = Product.builder().id(UUID.randomUUID()).company(company)
                .status(ProductStatus.ACTIVE).build();
        Product optional = Product.builder().id(UUID.randomUUID()).company(company)
                .status(ProductStatus.ACTIVE).build();
        DesignRequest request = DesignRequest.builder()
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .mode(DesignRequestMode.REDESIGN)
                .scope(DesignRequestScope.FULL)
                .build();
        request.getProducts().add(DesignRequestProduct.builder()
                .designRequest(request).product(required).requiredFromBaseline(true).build());
        when(productRepository.findByIdInAndCompanyId(List.of(optional.getId()), company.getId()))
                .thenReturn(List.of(optional));

        new DesignRequestProductService(requestProductRepository, productRepository, hotspotRepository)
                .replaceOptionalProducts(request, List.of(optional.getId()));

        assertEquals(2, request.getProducts().size());
        assertEquals(1, request.getProducts().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequiredFromBaseline())).count());
    }

    @Test
    void spatialRequestRejectsProductSelection() {
        DesignRequest request = DesignRequest.builder()
                .scope(DesignRequestScope.SPATIAL)
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
        AppException exception = assertThrows(AppException.class,
                () -> new DesignRequestProductService(requestProductRepository, productRepository, hotspotRepository)
                        .initializeAllowlist(request, List.of(UUID.randomUUID())));

        assertSame(ErrorCode.DESIGN_PRODUCT_ACCESS_FORBIDDEN, exception.getErrorCode());
    }
}
