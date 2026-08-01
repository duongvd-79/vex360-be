package com.example.vex360.features.booth.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothLifecycleService {

    private final BoothRepository boothRepository;
    private final BoothReviewRequestRepository boothReviewRequestRepository;

    @Transactional
    public void handleExhibitionActivated(Integer exhibitionId) {
        applyLifecycle(exhibitionId, false);
    }

    @Transactional
    public void handleExhibitionCompleted(Integer exhibitionId) {
        applyLifecycle(exhibitionId, true);
    }

    private void applyLifecycle(Integer exhibitionId, boolean archivePublished) {
        List<Booth> booths = boothRepository.findBoothsByExhibitionId(exhibitionId);
        List<Booth> realBooths = booths.stream()
                .filter(b -> b.getIsTemplate() == null || !b.getIsTemplate())
                .toList();
        if (realBooths.isEmpty()) {
            return;
        }

        realBooths.stream()
                .filter(booth -> archivePublished || booth.getStatus() != BoothStatus.PUBLISHED)
                .forEach(booth -> booth.setStatus(BoothStatus.ARCHIVED));

        List<BoothReviewRequest> pendingReviews = boothReviewRequestRepository
                .findByBoothIdInAndStatus(realBooths.stream().map(Booth::getId).toList(), BoothReviewStatus.PENDING);
        for (BoothReviewRequest review : pendingReviews) {
            review.setStatus(BoothReviewStatus.CANCELED);
            review.setCanceledAt(Instant.now());
            review.setCancellationReason("EXHIBITION_LIFECYCLE");
        }
    }
}
