package com.example.vex360.features.exhibition.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.vex360.features.exhibition.events.ExhibitionActivatedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.shared.enums.ExhibitionStatus;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExhibitionLifecycleService {

    private final ExhibitionRepository exhibitionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public ExhibitionLifecycleService(
            ExhibitionRepository exhibitionRepository,
            ApplicationEventPublisher eventPublisher,
            ExhibitionTimelinePolicy timelinePolicy,
            PlatformTransactionManager transactionManager) {
        this.exhibitionRepository = exhibitionRepository;
        this.eventPublisher = eventPublisher;
        this.timelinePolicy = timelinePolicy;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int processLifecycleTransitions() {
        LocalDate today = timelinePolicy.today();
        List<Integer> dueIds = exhibitionRepository.findDueForLifecycleTransition(today,
                today.plusDays(ExhibitionTimelinePolicy.REGISTRATION_DEADLINE_DAYS_BEFORE_START));
        int updatedCount = 0;

        for (Integer exhibitionId : dueIds) {
            try {
                Boolean transitioned = requiresNewTransactionTemplate
                        .execute(status -> processSingleExhibitionTransition(exhibitionId, today));
                if (Boolean.TRUE.equals(transitioned)) {
                    updatedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to transition exhibition {}", exhibitionId, ex);
            }
        }

        return updatedCount;
    }

    public boolean processSingleExhibitionTransition(Integer exhibitionId, LocalDate today) {
        return exhibitionRepository.findByIdForUpdate(exhibitionId).map(exhibition -> {
            ExhibitionStatus targetStatus = timelinePolicy.resolveTargetStatus(exhibition, today);
            if (targetStatus == null || targetStatus == exhibition.getStatus()) {
                return false;
            }

            ExhibitionStatus oldStatus = exhibition.getStatus();
            exhibition.setStatus(targetStatus);
            exhibitionRepository.save(exhibition);

            log.info("[LIFECYCLE_TRANSITION] Exhibition {} ({}) status changed from {} to {}",
                    exhibition.getName(), exhibition.getId(), oldStatus, targetStatus);

            if (targetStatus == ExhibitionStatus.ACTIVE) {
                eventPublisher.publishEvent(new ExhibitionActivatedEvent(this, exhibition));
            } else if (targetStatus == ExhibitionStatus.COMPLETED) {
                eventPublisher.publishEvent(new ExhibitionCompletedEvent(this, exhibition));
            }

            return true;
        }).orElse(false);
    }
}
