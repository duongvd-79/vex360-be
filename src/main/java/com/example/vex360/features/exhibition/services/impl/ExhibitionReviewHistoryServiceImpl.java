package com.example.vex360.features.exhibition.services.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewHistoryResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewSnapshotResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.features.exhibition.entities.ExhibitionReviewRequest;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;
import com.example.vex360.features.exhibition.mapper.ExhibitionReviewHistoryMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionReviewRequestRepository;
import com.example.vex360.features.exhibition.services.ExhibitionReviewHistoryService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.exceptions.AppException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionReviewHistoryServiceImpl implements ExhibitionReviewHistoryService {

    private final ExhibitionReviewRequestRepository reviewRequestRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionAssetRepository exhibitionAssetRepository;
    private final ExhibitionReviewHistoryMapper mapper;

    @Override
    @Transactional
    public void recordInitialSubmission(Exhibition exhibition, User submittedBy, String keyVisualUrl) {
        String resolvedKeyVisualUrl = resolveKeyVisualUrl(exhibition, keyVisualUrl);
        String snapshotJson = buildSnapshotJson(exhibition, resolvedKeyVisualUrl);

        ExhibitionReviewRequest reviewRequest = ExhibitionReviewRequest.builder()
                .exhibition(exhibition)
                .versionNumber(1)
                .status(ExhibitionReviewStatus.PENDING)
                .submittedBy(submittedBy)
                .submittedAt(Instant.now())
                .contentSnapshotJson(snapshotJson)
                .legacyIncomplete(false)
                .build();

        reviewRequestRepository.save(reviewRequest);
    }

    @Override
    @Transactional
    public void recordResubmissionOrUpdate(Exhibition exhibition, User submittedBy, String keyVisualUrl) {
        String resolvedKeyVisualUrl = resolveKeyVisualUrl(exhibition, keyVisualUrl);
        String snapshotJson = buildSnapshotJson(exhibition, resolvedKeyVisualUrl);

        Optional<ExhibitionReviewRequest> latestOpt = reviewRequestRepository
                .findFirstByExhibitionIdOrderByVersionNumberDesc(exhibition.getId());

        if (latestOpt.isPresent() && latestOpt.get().getStatus() == ExhibitionReviewStatus.PENDING) {
            ExhibitionReviewRequest currentRound = latestOpt.get();
            currentRound.setContentSnapshotJson(snapshotJson);
            reviewRequestRepository.save(currentRound);
        } else {
            int newVersion = latestOpt.map(r -> r.getVersionNumber() + 1).orElse(1);
            ExhibitionReviewRequest newRound = ExhibitionReviewRequest.builder()
                    .exhibition(exhibition)
                    .versionNumber(newVersion)
                    .status(ExhibitionReviewStatus.PENDING)
                    .submittedBy(submittedBy)
                    .submittedAt(Instant.now())
                    .contentSnapshotJson(snapshotJson)
                    .legacyIncomplete(false)
                    .build();

            reviewRequestRepository.save(newRound);
        }
    }

    @Override
    @Transactional
    public void recordReviewResult(Exhibition exhibition, User admin, ExhibitionReviewStatus newStatus,
            String rejectedReason) {
        Optional<ExhibitionReviewRequest> pendingRoundOpt = reviewRequestRepository
                .findFirstByExhibitionIdAndStatusForUpdate(exhibition.getId(), ExhibitionReviewStatus.PENDING);

        Instant now = Instant.now();
        if (pendingRoundOpt.isPresent()) {
            ExhibitionReviewRequest pendingRound = pendingRoundOpt.get();
            pendingRound.setStatus(newStatus);
            pendingRound.setReviewedBy(admin);
            pendingRound.setReviewedAt(now);
            pendingRound.setRejectedReason(newStatus == ExhibitionReviewStatus.REJECTED ? rejectedReason : null);
            reviewRequestRepository.save(pendingRound);
        } else {
            Optional<ExhibitionReviewRequest> latestOpt = reviewRequestRepository
                    .findFirstByExhibitionIdOrderByVersionNumberDesc(exhibition.getId());
            int newVersion = latestOpt.map(r -> r.getVersionNumber() + 1).orElse(1);

            ExhibitionReviewRequest terminalRound = ExhibitionReviewRequest.builder()
                    .exhibition(exhibition)
                    .versionNumber(newVersion)
                    .status(newStatus)
                    .reviewedBy(admin)
                    .reviewedAt(now)
                    .rejectedReason(newStatus == ExhibitionReviewStatus.REJECTED ? rejectedReason : null)
                    .legacyIncomplete(false)
                    .build();

            reviewRequestRepository.save(terminalRound);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExhibitionReviewHistoryResponseDTO> getReviewHistoryForAdmin(UUID exhibitionUuid) {
        exhibitionRepository.findByUuid(exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        List<ExhibitionReviewRequest> requests = reviewRequestRepository
                .findByExhibitionUuidOrderByVersionNumberDesc(exhibitionUuid);

        return requests.stream().map(mapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExhibitionReviewHistoryResponseDTO> getReviewHistoryForOrganizer(User organizer, UUID exhibitionUuid) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
        }

        List<ExhibitionReviewRequest> requests = reviewRequestRepository
                .findByExhibitionUuidOrderByVersionNumberDesc(exhibitionUuid);

        return requests.stream().map(mapper::toDTO).toList();
    }

    private String resolveKeyVisualUrl(Exhibition exhibition, String keyVisualUrl) {
        if (keyVisualUrl != null && !keyVisualUrl.isBlank()) {
            return keyVisualUrl;
        }

        return exhibitionAssetRepository.findByExhibitionIdAndType(exhibition.getId(), ExhibitionAssetType.KEY_VISUAL)
                .map(ExhibitionAsset::getAssetUrl)
                .orElse(null);
    }

    private String buildSnapshotJson(Exhibition exhibition, String keyVisualUrl) {
        ExhibitionReviewSnapshotResponseDTO snapshot = mapper.toSnapshot(exhibition, keyVisualUrl);
        return mapper.toSnapshotJson(snapshot);
    }
}
