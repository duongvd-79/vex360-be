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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionActivatedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.services.ExhibitionLifecycleService;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.shared.enums.ExhibitionStatus;

class ExhibitionLifecycleServiceTest {

    private ExhibitionRepository exhibitionRepository;
    private ApplicationEventPublisher eventPublisher;
    private Clock clock;
    private ExhibitionTimelinePolicy timelinePolicy;
    private ExhibitionLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        exhibitionRepository = mock(ExhibitionRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        // Fixed time at 2026-01-10T10:00:00Z -> today is 2026-01-10
        clock = Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC);
        timelinePolicy = new ExhibitionTimelinePolicy(clock);

        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        };

        lifecycleService = new ExhibitionLifecycleService(exhibitionRepository, eventPublisher, timelinePolicy,
                txManager);
    }

    @Test
    @DisplayName("Should transition REGISTRATION exhibition to PUBLISHED at T-7")
    void testTransitionToPublished() {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Upcoming Expo")
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.JANUARY, 17)) // today is Jan 10 (T-7)
                .endDate(LocalDate.of(2026, Month.JANUARY, 25))
                .build();

        LocalDate today = LocalDate.of(2026, Month.JANUARY, 10);
        when(exhibitionRepository.findDueForLifecycleTransition(eq(today), any())).thenReturn(List.of(1));
        when(exhibitionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(exhibition));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.PUBLISHED, exhibition.getStatus());
        verify(exhibitionRepository).save(exhibition);
    }

    @Test
    @DisplayName("Should transition PUBLISHED exhibition to ACTIVE on or after startDate and publish ExhibitionActivatedEvent")
    void testTransitionToActive() {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Tech Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.of(2026, Month.JANUARY, 10))
                .endDate(LocalDate.of(2026, Month.JANUARY, 15))
                .build();

        LocalDate today = LocalDate.of(2026, Month.JANUARY, 10);
        when(exhibitionRepository.findDueForLifecycleTransition(eq(today), any())).thenReturn(List.of(1));
        when(exhibitionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(exhibition));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.ACTIVE, exhibition.getStatus());
        verify(exhibitionRepository).save(exhibition);
        verify(eventPublisher).publishEvent(any(ExhibitionActivatedEvent.class));
    }

    @Test
    @DisplayName("Should transition ACTIVE exhibition to COMPLETED after endDate and publish ExhibitionCompletedEvent")
    void testTransitionToCompleted() {
        Exhibition exhibition = Exhibition.builder()
                .id(2)
                .name("Old Expo")
                .status(ExhibitionStatus.ACTIVE)
                .startDate(LocalDate.of(2026, Month.JANUARY, 1))
                .endDate(LocalDate.of(2026, Month.JANUARY, 9))
                .build();

        LocalDate today = LocalDate.of(2026, Month.JANUARY, 10);
        when(exhibitionRepository.findDueForLifecycleTransition(eq(today), any())).thenReturn(List.of(2));
        when(exhibitionRepository.findByIdForUpdate(2)).thenReturn(Optional.of(exhibition));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.COMPLETED, exhibition.getStatus());
        verify(exhibitionRepository).save(exhibition);
        verify(eventPublisher).publishEvent(any(ExhibitionCompletedEvent.class));
    }

    @Test
    @DisplayName("Should handle failure for one exhibition without stopping others")
    void testPartialFailureIsolation() {
        Exhibition ex2 = Exhibition.builder()
                .id(2)
                .name("Second Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.of(2026, Month.JANUARY, 10))
                .endDate(LocalDate.of(2026, Month.JANUARY, 15))
                .build();

        LocalDate today = LocalDate.of(2026, Month.JANUARY, 10);
        when(exhibitionRepository.findDueForLifecycleTransition(eq(today), any())).thenReturn(List.of(1, 2));
        when(exhibitionRepository.findByIdForUpdate(1)).thenThrow(new RuntimeException("DB Lock error"));
        when(exhibitionRepository.findByIdForUpdate(2)).thenReturn(Optional.of(ex2));

        int updated = lifecycleService.processLifecycleTransitions();

        assertEquals(1, updated);
        assertEquals(ExhibitionStatus.ACTIVE, ex2.getStatus());
    }
}
