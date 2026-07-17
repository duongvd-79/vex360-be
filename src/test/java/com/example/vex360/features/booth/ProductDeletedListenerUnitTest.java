package com.example.vex360.features.booth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.listeners.ProductDeletedListener;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.events.ProductDeletedEvent;

@ExtendWith(MockitoExtension.class)
class ProductDeletedListenerUnitTest {

    @Mock
    private HotspotRepository hotspotRepository;

    @InjectMocks
    private ProductDeletedListener listener;

    @Test
    void deletesHotspotsThatReferenceDeletedProduct() {
        Product product = Product.builder().build();
        List<Hotspot> hotspots = List.of(Hotspot.builder().build());
        when(hotspotRepository.findByProduct(product)).thenReturn(hotspots);

        listener.handleProductDeleted(new ProductDeletedEvent(this, product));

        verify(hotspotRepository).deleteAll(hotspots);
    }
}
