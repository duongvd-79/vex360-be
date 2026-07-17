package com.example.vex360.features.booth.listeners;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.product.events.ProductDeletedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductDeletedListener {

    private final HotspotRepository hotspotRepository;

    @EventListener
    public void handleProductDeleted(ProductDeletedEvent event) {
        List<Hotspot> hotspots = hotspotRepository.findByProduct(event.getProduct());
        if (!hotspots.isEmpty()) {
            hotspotRepository.deleteAll(hotspots);
        }
    }
}
