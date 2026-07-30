package com.example.vex360.features.exhibition.services;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.shared.enums.ExhibitionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionLifecycleService {

    private final ExhibitionRepository exhibitionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public int processLifecycleTransitions() {
        LocalDate today = LocalDate.now(clock);
        int updatedCount = 0;

        // 1. Transition PUBLISHED -> ACTIVE when startDate <= today
        Page<Exhibition> publishedPage = exhibitionRepository.findByStatusAndStartDateLessThanEqual(
                ExhibitionStatus.PUBLISHED, today, PageRequest.of(0, 100));

        for (Exhibition e : publishedPage.getContent()) {
            try {
                if (transitionToActive(e.getId(), today)) {
                    updatedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to transition exhibition {} to ACTIVE", e.getId(), ex);
            }
        }

        // 2. Transition ACTIVE -> COMPLETED when endDate < today
        Page<Exhibition> activePage = exhibitionRepository.findByStatusAndEndDateLessThan(
                ExhibitionStatus.ACTIVE, today, PageRequest.of(0, 100));

        for (Exhibition e : activePage.getContent()) {
            try {
                if (transitionToCompleted(e.getId(), today)) {
                    updatedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to transition exhibition {} to COMPLETED", e.getId(), ex);
            }
        }

        return updatedCount;
    }

    @Transactional
    public boolean transitionToActive(Integer exhibitionId, LocalDate today) {
        return exhibitionRepository.findByIdForUpdate(exhibitionId).map(exhibition -> {
            if (exhibition.getStatus() == ExhibitionStatus.PUBLISHED
                    && exhibition.getStartDate() != null
                    && !exhibition.getStartDate().isAfter(today)) {
                exhibition.setStatus(ExhibitionStatus.ACTIVE);
                exhibitionRepository.save(exhibition);
                log.info("[LIFECYCLE_TRANSITION] Exhibition {} ({}) status changed from PUBLISHED to ACTIVE",
                        exhibition.getName(), exhibition.getId());
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Transactional
    public boolean transitionToCompleted(Integer exhibitionId, LocalDate today) {
        return exhibitionRepository.findByIdForUpdate(exhibitionId).map(exhibition -> {
            if (exhibition.getStatus() == ExhibitionStatus.ACTIVE
                    && exhibition.getEndDate() != null
                    && exhibition.getEndDate().isBefore(today)) {
                exhibition.setStatus(ExhibitionStatus.COMPLETED);
                exhibitionRepository.save(exhibition);
                log.info("[LIFECYCLE_TRANSITION] Exhibition {} ({}) status changed from ACTIVE to COMPLETED",
                        exhibition.getName(), exhibition.getId());

                eventPublisher.publishEvent(new ExhibitionCompletedEvent(this, exhibition));
                return true;
            }
            return false;
        }).orElse(false);
    }
}
