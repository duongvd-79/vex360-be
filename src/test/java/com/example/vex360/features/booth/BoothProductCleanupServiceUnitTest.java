package com.example.vex360.features.booth;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.services.BoothProductCleanupService;
import com.example.vex360.features.product.entities.Product;

@ExtendWith(MockitoExtension.class)
class BoothProductCleanupServiceUnitTest {
    @Mock
    private HotspotRepository hotspotRepository;

    @InjectMocks
    private BoothProductCleanupService service;

    @Test
    void removesHotspotsThatReferenceProduct() {
        Product product = Product.builder().build();
        List<Hotspot> hotspots = List.of(Hotspot.builder().build());
        when(hotspotRepository.findByProduct(product)).thenReturn(hotspots);

        service.removeHotspotsForProduct(product);

        verify(hotspotRepository).deleteAll(hotspots);
    }

    @Test
    void skipsDeleteWhenProductHasNoHotspots() {
        Product product = Product.builder().build();
        when(hotspotRepository.findByProduct(product)).thenReturn(List.of());

        service.removeHotspotsForProduct(product);

        verify(hotspotRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }
}
