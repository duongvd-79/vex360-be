package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.BoothDesignService.HotspotDesign;
import com.example.vex360.features.booth.services.BoothDesignService.PanoramaDesign;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;

@ExtendWith(MockitoExtension.class)
class BoothDesignServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private HotspotRepository hotspotRepository;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private PanoramaImageCleanupService panoramaImageCleanupService;

    private BoothDesignService service;

    @BeforeEach
    void setup() {
        service = new BoothDesignService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                mediaAssetRepository,
                panoramaImageCleanupService);
    }

    @Test
    void replaceBoothContentDeletesOldPanoramasAndAppliesNewContent() {
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .name("Video")
                .type(MediaAssetType.VIDEO)
                .build();
        Booth booth = Booth.builder().id(UUID.randomUUID()).name("Booth").build();
        Panorama oldDesignerPanorama = Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name("Old")
                .build();
        booth.getPanoramas().add(oldDesignerPanorama);
        PanoramaDesign panoramaDesign = new PanoramaDesign(
                "p1",
                "Entrance",
                "https://cdn.example.com/pano.jpg",
                "pano-key",
                25L,
                0,
                true,
                false,
                List.of(new HotspotDesign(
                        HotspotType.MEDIA,
                        "Video",
                        null,
                        null,
                        mediaAsset,
                        null,
                        1.0,
                        2.0,
                        3.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

        when(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()))
                .thenReturn(List.of(oldDesignerPanorama));
        when(panoramaRepository.save(any(Panorama.class))).thenAnswer(invocation -> {
            Panorama panorama = invocation.getArgument(0);
            panorama.setId(UUID.randomUUID());
            return panorama;
        });

        service.replaceBoothContent(booth, List.of(panoramaDesign));

        verify(hotspotRepository).clearTargetsForPanoramas(List.of(oldDesignerPanorama.getId()));
        verify(panoramaRepository).deleteAll(List.of(oldDesignerPanorama));
        verify(panoramaImageCleanupService).scheduleCleanup(Set.of());

        ArgumentCaptor<Panorama> panoramaCaptor = ArgumentCaptor.forClass(Panorama.class);
        verify(panoramaRepository).save(panoramaCaptor.capture());
        Panorama savedPanorama = panoramaCaptor.getValue();
        assertEquals("Entrance", savedPanorama.getName());
        assertEquals(List.of(savedPanorama), booth.getPanoramas());

        ArgumentCaptor<Hotspot> hotspotCaptor = ArgumentCaptor.forClass(Hotspot.class);
        verify(hotspotRepository).save(hotspotCaptor.capture());
        Hotspot savedHotspot = hotspotCaptor.getValue();
        assertEquals(HotspotType.MEDIA, savedHotspot.getType());
        assertSame(mediaAsset, savedHotspot.getMediaAsset());
    }
}
