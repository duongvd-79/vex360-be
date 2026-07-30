package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.services.ExhibitionLifecycleService;
import com.example.vex360.shared.enums.ExhibitionStatus;

class ExhibitionLifecycleServiceTest {

    private ExhibitionRepository exhibitionRepository;
    private ApplicationEventPublisher eventPublisher;
    private Clock clock;
    private ExhibitionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        exhibitionRepository = mock(ExhibitionRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // Fixed time at 2026-01-10T10:00:00Z -> today is 2026-01-10
        clock = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);
        lifecycleService = new ExhibitionLifecycleService(exhibitionRepository, eventPublisher, clock);
    }

    @Test
    @DisplayName("Should transition PUBLISHED exhibition to ACTIVE on or after startDate")
    void testTransitionToActive() {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Tech Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.of(2026, Month.JANUARY, 10))
                .build();

        when(exhibitionRepository.findByStatusAndStartDateLessThanEqual(eq(ExhibitionStatus.PUBLISHED), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exhibition)));
        when(exhibitionRepository.findByStatusAndEndDateLessThan(eq(ExhibitionStatus.ACTIVE), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(exhibitionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(exhibition));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.ACTIVE, exhibition.getStatus());
        verify(exhibitionRepository).save(exhibition);
    }

    @Test
    @DisplayName("Should transition ACTIVE exhibition to COMPLETED after endDate")
    void testTransitionToCompleted() {
        Exhibition exhibition = Exhibition.builder()
                .id(2)
                .name("Old Expo")
                .status(ExhibitionStatus.ACTIVE)
                .endDate(LocalDate.of(2026, Month.JANUARY, 9))
                .build();

        when(exhibitionRepository.findByStatusAndStartDateLessThanEqual(eq(ExhibitionStatus.PUBLISHED), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(exhibitionRepository.findByStatusAndEndDateLessThan(eq(ExhibitionStatus.ACTIVE), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exhibition)));
        when(exhibitionRepository.findByIdForUpdate(2)).thenReturn(Optional.of(exhibition));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.COMPLETED, exhibition.getStatus());
        verify(exhibitionRepository).save(exhibition);
        verify(eventPublisher).publishEvent(any(ExhibitionCompletedEvent.class));
    }
}
