package com.example.vex360.features.booth.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.product.entities.Product;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothProductCleanupService {
    private final HotspotRepository hotspotRepository;

    @Transactional
    public void removeHotspotsForProduct(Product product) {
        List<Hotspot> hotspots = hotspotRepository.findByProduct(product);
        if (!hotspots.isEmpty()) {
            hotspotRepository.deleteAll(hotspots);
        }
    }
}
