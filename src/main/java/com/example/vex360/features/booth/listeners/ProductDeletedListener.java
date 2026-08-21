package com.example.vex360.features.booth.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.services.BoothProductCleanupService;
import com.example.vex360.features.product.events.ProductDeletedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductDeletedListener {

    private final BoothProductCleanupService boothProductCleanupService;

    @EventListener
    public void handleProductDeleted(ProductDeletedEvent event) {
        boothProductCleanupService.removeHotspotsForProduct(event.getProduct());
    }
}
