package com.example.vex360.features.booth;

import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.listeners.ProductDeletedListener;
import com.example.vex360.features.booth.services.BoothProductCleanupService;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.events.ProductDeletedEvent;

@ExtendWith(MockitoExtension.class)
class ProductDeletedListenerUnitTest {

    @Mock
    private BoothProductCleanupService boothProductCleanupService;

    @InjectMocks
    private ProductDeletedListener listener;

    @Test
    void delegatesProductCleanupToApplicationService() {
        Product product = Product.builder().build();

        listener.handleProductDeleted(new ProductDeletedEvent(this, product));

        verify(boothProductCleanupService).removeHotspotsForProduct(product);
    }
}
