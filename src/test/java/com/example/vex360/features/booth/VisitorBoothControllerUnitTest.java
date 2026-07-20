package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.booth.controllers.VisitorBoothController;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.dtos.response.VisitorProductSearchResponseDTO;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.BoothListingPriority;

@ExtendWith(MockitoExtension.class)
class VisitorBoothControllerUnitTest {

    @Mock
    private VisitorBoothService visitorBoothService;

    private VisitorBoothController controller;

    @BeforeEach
    void setup() {
        controller = new VisitorBoothController(visitorBoothService);
    }

    @Test
    void getPublishedBooths_DelegatesToService() {
        UUID exhibitionUuid = UUID.randomUUID();
        String keyword = "test";
        BoothListingPriority listingPriority = BoothListingPriority.FEATURED;
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<BoothResponseDTO> pageResponse = PageResponse.<BoothResponseDTO>builder()
                .content(List.of(new BoothResponseDTO()))
                .build();

        when(visitorBoothService.getPublishedBooths(exhibitionUuid, keyword, listingPriority, pageable))
                .thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> response =
                controller.getPublishedBooths(exhibitionUuid, keyword, listingPriority, pageable);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(pageResponse, response.getBody().data());
        verify(visitorBoothService).getPublishedBooths(exhibitionUuid, keyword, listingPriority, pageable);
    }

    @Test
    void searchDisplayedProducts_DelegatesToService() {
        UUID exhibitionUuid = UUID.randomUUID();
        String keyword = "robot";
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<VisitorProductSearchResponseDTO> pageResponse = PageResponse
                .<VisitorProductSearchResponseDTO>builder()
                .content(List.of(new VisitorProductSearchResponseDTO()))
                .build();

        when(visitorBoothService.searchDisplayedProducts(exhibitionUuid, keyword, pageable))
                .thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<VisitorProductSearchResponseDTO>>> response =
                controller.searchDisplayedProducts(exhibitionUuid, keyword, pageable);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(pageResponse, response.getBody().data());
        verify(visitorBoothService).searchDisplayedProducts(exhibitionUuid, keyword, pageable);
    }

    @Test
    void getDisplayedProductDetail_DelegatesToService() {
        UUID exhibitionUuid = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductResponseDTO detail = new ProductResponseDTO();

        when(visitorBoothService.getDisplayedProductDetail(exhibitionUuid, productId))
                .thenReturn(detail);

        ResponseEntity<ApiResponse<ProductResponseDTO>> response =
                controller.getDisplayedProductDetail(exhibitionUuid, productId);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(detail, response.getBody().data());
        verify(visitorBoothService).getDisplayedProductDetail(exhibitionUuid, productId);
    }

    @Test
    void getBoothTourDetail_DelegatesToService() {
        UUID exhibitionUuid = UUID.randomUUID();
        UUID boothId = UUID.randomUUID();
        BoothResponseDTO detail = new BoothResponseDTO();

        when(visitorBoothService.getBoothTourDetail(exhibitionUuid, boothId))
                .thenReturn(detail);

        ResponseEntity<ApiResponse<BoothResponseDTO>> response =
                controller.getBoothTourDetail(exhibitionUuid, boothId);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(detail, response.getBody().data());
        verify(visitorBoothService).getBoothTourDetail(exhibitionUuid, boothId);
    }
}
