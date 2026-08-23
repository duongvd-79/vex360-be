package com.example.vex360.features.hall.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.hall.dtos.request.RejectHallReviewRequest;
import com.example.vex360.features.hall.dtos.response.HallReviewRequestDetailDTO;
import com.example.vex360.features.hall.dtos.response.HallReviewRequestSummaryDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallPublishedRevision;
import com.example.vex360.features.hall.entities.HallReviewRequest;
import com.example.vex360.features.hall.enums.HallReviewStatus;
import com.example.vex360.features.hall.enums.HallStatus;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.features.hall.repositories.ExhibitionHallRepository;
import com.example.vex360.features.hall.repositories.HallPublishedRevisionRepository;
import com.example.vex360.features.hall.repositories.HallReviewRequestRepository;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionExperienceMode;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HallReviewService {
    private final ExhibitionHallService hallService;
    private final ExhibitionHallRepository hallRepository;
    private final HallReviewRequestRepository reviewRequestRepository;
    private final HallPublishedRevisionRepository publishedRevisionRepository;
    private final HallReviewSnapshotFactory snapshotFactory;
    private final HallReviewDiffService diffService;
    private final MailService mailService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    @Transactional
    public HallReviewRequestSummaryDTO submit(User organizer, UUID exhibitionUuid) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        if (reviewRequestRepository.existsByHallIdAndStatus(hall.getId(), HallReviewStatus.PENDING)) {
            throw new AppException(ErrorCode.HALL_REVIEW_PENDING_EXISTS);
        }

        HallReviewSnapshot snapshot = snapshotFactory.create(hall);
        validateSnapshot(snapshot);
        HallReviewRequest previous = reviewRequestRepository
                .findTopByHallIdOrderByVersionNumberDescSubmittedAtDesc(hall.getId())
                .orElse(null);
        int versionNumber = Math.toIntExact(reviewRequestRepository.countByHallId(hall.getId()) + 1);
        var changeSummary = diffService.buildSummary(snapshot, previous, versionNumber);
        HallReviewRequest request = HallReviewRequest.builder()
                .hall(hall)
                .status(HallReviewStatus.PENDING)
                .submittedBy(organizer)
                .contentSnapshotJson(diffService.writeJson(snapshot))
                .changeSummaryJson(diffService.writeJson(changeSummary))
                .versionNumber(versionNumber)
                .build();
        hall.setStatus(HallStatus.PENDING_REVIEW);
        hallRepository.save(hall);
        return toSummary(reviewRequestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public PageResponse<HallReviewRequestSummaryDTO> getOrganizerHistory(
            User organizer, UUID exhibitionUuid, Pageable pageable) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return PageResponse.from(reviewRequestRepository
                .findByHallIdOrderBySubmittedAtDesc(hall.getId(), pageable)
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<HallReviewRequestSummaryDTO> getAdminRequests(
            User admin, HallReviewStatus status, String keyword, Pageable pageable) {
        assertAdmin(admin);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return PageResponse.from(reviewRequestRepository
                .searchForAdmin(status, normalizedKeyword, pageable)
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public HallReviewRequestDetailDTO getAdminRequest(User admin, UUID requestId) {
        assertAdmin(admin);
        HallReviewRequest request = findRequest(requestId);
        return HallReviewRequestDetailDTO.builder()
                .reviewRequest(toSummary(request))
                .contentSnapshot(diffService.readSnapshot(request.getContentSnapshotJson()))
                .build();
    }

    @Transactional
    public HallReviewRequestSummaryDTO approve(User admin, UUID requestId) {
        assertAdmin(admin);
        HallReviewRequest request = findRequestForUpdate(requestId);
        assertPending(request);
        if (!LocalDate.now(clock).isBefore(request.getHall().getExhibition().getStartDate())) {
            throw new AppException(ErrorCode.HALL_EXHIBITION_ALREADY_STARTED);
        }
        if (publishedRevisionRepository.findByHallId(request.getHall().getId()).isPresent()) {
            throw new AppException(ErrorCode.HALL_REVIEW_INVALID_STATUS);
        }

        Instant now = Instant.now(clock);
        request.setStatus(HallReviewStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(now);
        request.setRejectedReason(null);
        request.getHall().setStatus(HallStatus.PUBLISHED);
        publishedRevisionRepository.save(HallPublishedRevision.builder()
                .hall(request.getHall())
                .versionNumber(request.getVersionNumber())
                .contentSnapshotJson(request.getContentSnapshotJson())
                .publishedAt(now)
                .build());
        hallRepository.save(request.getHall());
        HallReviewRequest saved = reviewRequestRepository.saveAndFlush(request);
        notifyResult(saved);
        return toSummary(saved);
    }

    @Transactional
    public HallReviewRequestSummaryDTO reject(
            User admin, UUID requestId, RejectHallReviewRequest rejectRequest) {
        assertAdmin(admin);
        HallReviewRequest request = findRequestForUpdate(requestId);
        assertPending(request);
        if (rejectRequest == null || rejectRequest.getRejectedReason() == null
                || rejectRequest.getRejectedReason().isBlank()) {
            throw new AppException(ErrorCode.HALL_REVIEW_REJECTION_REASON_REQUIRED);
        }

        request.setStatus(HallReviewStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(Instant.now(clock));
        request.setRejectedReason(rejectRequest.getRejectedReason().trim());
        request.getHall().setStatus(HallStatus.REJECTED);
        hallRepository.save(request.getHall());
        HallReviewRequest saved = reviewRequestRepository.saveAndFlush(request);
        notifyResult(saved);
        return toSummary(saved);
    }

    private void validateSnapshot(HallReviewSnapshot snapshot) {
        if (snapshot == null || snapshot.getHall() == null
                || snapshot.getPanoramas() == null || snapshot.getPanoramas().isEmpty()
                || snapshot.getHotspots() == null || snapshot.getItems() == null
                || snapshot.getMediaAssets() == null) {
            notReady();
        }
        long defaultCount = snapshot.getPanoramas().stream()
                .filter(panorama -> Boolean.TRUE.equals(panorama.getIsDefault())).count();
        if (defaultCount != 1) {
            notReady();
        }

        Set<UUID> panoramaIds = snapshot.getPanoramas().stream()
                .map(HallReviewSnapshot.PanoramaItem::getId).collect(Collectors.toSet());
        Set<UUID> itemIds = snapshot.getItems().stream()
                .map(HallReviewSnapshot.ContentItem::getId).collect(Collectors.toSet());
        Set<UUID> mediaIds = snapshot.getMediaAssets().stream()
                .map(HallReviewSnapshot.MediaAssetItem::getId).collect(Collectors.toSet());
        if (panoramaIds.contains(null) || itemIds.contains(null) || mediaIds.contains(null)
                || snapshot.getItems().stream().anyMatch(item -> !mediaIds.contains(item.getMediaAssetId()))) {
            notReady();
        }
        Map<UUID, MediaAssetType> mediaTypes = snapshot.getMediaAssets().stream()
                .collect(Collectors.toMap(
                        HallReviewSnapshot.MediaAssetItem::getId,
                        HallReviewSnapshot.MediaAssetItem::getType));

        Set<Integer> boothSlots = new HashSet<>();
        for (HallReviewSnapshot.HotspotItem hotspot : snapshot.getHotspots()) {
            if (hotspot.getId() == null || hotspot.getType() == null || hotspot.getName() == null
                    || hotspot.getName().isBlank() || !panoramaIds.contains(hotspot.getSourcePanoramaId())
                    || hotspot.getXPosition() == null || hotspot.getYPosition() == null
                    || hotspot.getZPosition() == null) {
                notReady();
            }
            switch (hotspot.getType()) {
                case NAV -> {
                    if (!panoramaIds.contains(hotspot.getTargetPanoramaId())
                            || hotspot.getSourcePanoramaId().equals(hotspot.getTargetPanoramaId())) {
                        notReady();
                    }
                }
                case MEDIA -> {
                    if (!mediaIds.contains(hotspot.getMediaAssetId())) notReady();
                }
                case ITEM -> {
                    if (!itemIds.contains(hotspot.getItemId())) notReady();
                }
                case BOOTH_ENTRY -> validateBoothEntry(snapshot, hotspot, boothSlots);
                case INFO -> validateInfo(hotspot, itemIds, mediaTypes);
            }
        }
    }

    private void validateInfo(
            HallReviewSnapshot.HotspotItem hotspot,
            Set<UUID> itemIds,
            Map<UUID, MediaAssetType> mediaTypes) {
        HallInfoContentType contentType = hotspot.getInfoContentType();
        if (contentType == null || hotspot.getTargetPanoramaId() != null
                || hotspot.getBoothSlotIndex() != null || hotspot.getMediaClickAction() != null
                || hasAnyCorner(hotspot)) {
            notReady();
        }
        switch (contentType) {
            case NONE -> {
                if (hasText(hotspot.getInfoText())
                        || hotspot.getMediaAssetId() != null || hotspot.getItemId() != null) {
                    notReady();
                }
            }
            case TEXT -> {
                if (!hasText(hotspot.getInfoText())
                        || hotspot.getMediaAssetId() != null || hotspot.getItemId() != null) {
                    notReady();
                }
            }
            case IMAGE -> validateInfoMedia(hotspot, mediaTypes, MediaAssetType.IMAGE);
            case VIDEO -> validateInfoMedia(hotspot, mediaTypes, MediaAssetType.VIDEO);
            case ITEM -> {
                if (!itemIds.contains(hotspot.getItemId())
                        || hotspot.getMediaAssetId() != null || hasText(hotspot.getInfoText())) {
                    notReady();
                }
            }
        }
    }

    private void validateInfoMedia(
            HallReviewSnapshot.HotspotItem hotspot,
            Map<UUID, MediaAssetType> mediaTypes,
            MediaAssetType expectedType) {
        if (mediaTypes.get(hotspot.getMediaAssetId()) != expectedType
                || hotspot.getItemId() != null || hasText(hotspot.getInfoText())) {
            notReady();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasAnyCorner(HallReviewSnapshot.HotspotItem hotspot) {
        return hotspot.getCornerTlX() != null || hotspot.getCornerTlY() != null || hotspot.getCornerTlZ() != null
                || hotspot.getCornerTrX() != null || hotspot.getCornerTrY() != null
                || hotspot.getCornerTrZ() != null
                || hotspot.getCornerBlX() != null || hotspot.getCornerBlY() != null
                || hotspot.getCornerBlZ() != null
                || hotspot.getCornerBrX() != null || hotspot.getCornerBrY() != null
                || hotspot.getCornerBrZ() != null;
    }

    private void validateBoothEntry(
            HallReviewSnapshot snapshot,
            HallReviewSnapshot.HotspotItem hotspot,
            Set<Integer> boothSlots) {
        if (snapshot.getHall().getExperienceMode() != ExhibitionExperienceMode.WITH_BOOTHS
                || hotspot.getBoothSlotIndex() == null || hotspot.getBoothSlotIndex() < 0
                || !boothSlots.add(hotspot.getBoothSlotIndex())) {
            notReady();
        }
    }

    private void notReady() {
        throw new AppException(ErrorCode.HALL_REVIEW_NOT_READY);
    }

    private HallReviewRequest findRequest(UUID requestId) {
        return reviewRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.HALL_REVIEW_REQUEST_NOT_FOUND));
    }

    private HallReviewRequest findRequestForUpdate(UUID requestId) {
        return reviewRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.HALL_REVIEW_REQUEST_NOT_FOUND));
    }

    private void assertPending(HallReviewRequest request) {
        if (request.getStatus() != HallReviewStatus.PENDING
                || request.getHall().getStatus() != HallStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.HALL_REVIEW_INVALID_STATUS);
        }
    }

    private void assertAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private HallReviewRequestSummaryDTO toSummary(HallReviewRequest request) {
        User submitter = request.getSubmittedBy();
        User reviewer = request.getReviewedBy();
        return HallReviewRequestSummaryDTO.builder()
                .id(request.getId()).versionNumber(request.getVersionNumber())
                .hallId(request.getHall().getId()).hallName(request.getHall().getName())
                .exhibitionUuid(request.getHall().getExhibition().getUuid())
                .exhibitionName(request.getHall().getExhibition().getName())
                .status(request.getStatus())
                .submittedById(submitter == null ? null : submitter.getId())
                .submittedByName(submitter == null ? null : submitter.getFullName())
                .submittedAt(request.getSubmittedAt())
                .reviewedById(reviewer == null ? null : reviewer.getId())
                .reviewedByName(reviewer == null ? null : reviewer.getFullName())
                .reviewedAt(request.getReviewedAt()).rejectedReason(request.getRejectedReason())
                .changeSummary(diffService.readSummary(request.getChangeSummaryJson()))
                .build();
    }

    private void notifyResult(HallReviewRequest request) {
        User recipient = request.getSubmittedBy();
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) return;
        try {
            afterCommitExecutor.execute(() -> {
                try {
                    mailService.sendHallReviewResultEmail(
                            recipient.getEmail(), recipient.getFullName(), request.getHall().getName(),
                            request.getHall().getExhibition().getName(), request.getVersionNumber(),
                            request.getStatus().name(), request.getRejectedReason(), request.getReviewedAt());
                } catch (RuntimeException exception) {
                    log.warn("Could not send hall review result email for request {}", request.getId(), exception);
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Could not schedule hall review result email for request {}", request.getId(), exception);
        }
    }
}
