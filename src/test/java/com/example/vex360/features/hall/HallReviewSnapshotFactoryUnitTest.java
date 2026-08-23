package com.example.vex360.features.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallHotspot;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;
import com.example.vex360.features.hall.services.HallReviewSnapshotFactory;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;

@ExtendWith(MockitoExtension.class)
class HallReviewSnapshotFactoryUnitTest {
    @Mock
    private HallPanoramaRepository panoramaRepository;
    @Mock
    private HallHotspotRepository hotspotRepository;
    @Mock
    private HallItemRepository itemRepository;

    @Test
    void boothEntrySnapshotStoresStableSlotInsteadOfConcreteBooth() {
        UUID hallId = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().uuid(UUID.randomUUID()).name("Expo")
                .experienceMode(ExhibitionExperienceMode.WITH_BOOTHS).build();
        ExhibitionHall hall = ExhibitionHall.builder().id(hallId).exhibition(exhibition).name("Hall")
                .backgroundMusicUrl("https://cdn.example/hall.mp3")
                .backgroundMusicFileName("hall.mp3")
                .backgroundMusicFileSize(100L)
                .build();
        HallPanorama panorama = HallPanorama.builder().id(UUID.randomUUID()).hall(hall).name("Entrance")
                .imageUrl("pano.jpg").imageKey("pano-key").fileSize(100L)
                .orderIndex(0).isDefault(true).build();
        HallHotspot hotspot = HallHotspot.builder().id(UUID.randomUUID()).sourcePanorama(panorama)
                .type(HallHotspotType.BOOTH_ENTRY).name("Booth slot 3").boothSlotIndex(2)
                .xPosition(1.0).yPosition(2.0).zPosition(3.0).build();
        HallHotspot info = HallHotspot.builder().id(UUID.randomUUID()).sourcePanorama(panorama)
                .type(HallHotspotType.INFO).name("Welcome").infoText("Hello")
                .infoContentType(HallInfoContentType.TEXT)
                .xPosition(1.0).yPosition(2.0).zPosition(3.0).build();
        when(panoramaRepository.findByHallIdOrderByOrderIndexAsc(hallId)).thenReturn(List.of(panorama));
        when(hotspotRepository.findSnapshotHotspotsByHallId(hallId)).thenReturn(List.of(hotspot, info));
        when(itemRepository.findSnapshotItemsByHallId(hallId)).thenReturn(List.of());

        var snapshot = new HallReviewSnapshotFactory(
                panoramaRepository, hotspotRepository, itemRepository).create(hall);

        assertEquals(2, snapshot.getHotspots().getFirst().getBoothSlotIndex());
        assertNull(snapshot.getHotspots().getFirst().getTargetPanoramaId());
        assertEquals(ExhibitionExperienceMode.WITH_BOOTHS, snapshot.getHall().getExperienceMode());
        assertEquals(3, snapshot.getSnapshotSchemaVersion());
        assertEquals(HallInfoContentType.TEXT, snapshot.getHotspots().get(1).getInfoContentType());
        assertEquals(hall.getBackgroundMusicUrl(), snapshot.getHall().getBackgroundMusicUrl());
        assertEquals(hall.getBackgroundMusicFileName(), snapshot.getHall().getBackgroundMusicFileName());
        assertEquals(hall.getBackgroundMusicFileSize(), snapshot.getHall().getBackgroundMusicFileSize());
    }
}
