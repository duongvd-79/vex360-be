package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

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

@ExtendWith(MockitoExtension.class)
class DesignRequestProductServiceUnitTest {
    @Mock
    DesignRequestProductRepository requestProductRepository;
    @Mock
    ProductService productService;
    @Mock
    BoothDesignService boothDesignService;

    @Test
    void initialDesignAcceptsSelectedProducts() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        Product selected = Product.builder().id(UUID.randomUUID()).company(company)
                .status(ProductStatus.ACTIVE).build();
        DesignRequest request = DesignRequest.builder()
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
        when(productService.findProductsByIdsAndCompanyId(List.of(selected.getId()), company.getId()))
                .thenReturn(List.of(selected));

        new DesignRequestProductService(requestProductRepository, productService, boothDesignService)
                .initializeAllowlist(request, List.of(selected.getId()));

        assertEquals(1, request.getProducts().size());
        assertSame(selected, request.getProducts().get(0).getProduct());
    }

    @Test
    void initialDesignDeduplicatesSelectedProductIds() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        Product selected = Product.builder().id(UUID.randomUUID()).company(company)
                .status(ProductStatus.ACTIVE).build();
        DesignRequest request = DesignRequest.builder()
                .company(company)
                .booth(Booth.builder().id(UUID.randomUUID()).build())
                .mode(DesignRequestMode.INITIAL_DESIGN)
                .build();
        when(productService.findProductsByIdsAndCompanyId(List.of(selected.getId()), company.getId()))
                .thenReturn(List.of(selected));

        new DesignRequestProductService(requestProductRepository, productService, boothDesignService)
                .initializeAllowlist(request, List.of(selected.getId(), selected.getId()));

        assertEquals(1, request.getProducts().size());
        assertSame(selected, request.getProducts().get(0).getProduct());
    }
}
