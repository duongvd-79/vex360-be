package com.example.vex360.features.booth.services;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothContentOverviewDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewComparisonCompleteness;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoothReviewService {
    private final BoothRepository boothRepository;
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final CompanyService companyService;
    private final BoothMapper boothMapper;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final BoothReviewSnapshotFactory snapshotFactory;
    private final BoothReviewDiffService diffService;
    private final BoothReviewContentAssembler contentAssembler;
    private final ExhibitionService exhibitionService;
    private final MailService mailService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    @Transactional
    public BoothResponseDTO startEdit(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        Exhibition exhibition = getExhibition(booth);
        exhibitionService.findExhibitionForUpdate(exhibition.getId());
        boothReviewPolicyService.assertCanStartEdit(booth);
        booth.setStatus(BoothStatus.DRAFT);
        return boothMapper.toBoothResponseDTO(boothRepository.save(booth));
    }

    @Transactional
    public BoothReviewRequestSummaryDTO submitReview(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothByIdForUpdate(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        boothReviewPolicyService.assertCanSubmitReview(booth);
        booth.setLateEditAllowedUntil(null);

        BoothReviewRequest previousRequest = boothReviewRequestRepository
                .findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId())
                .orElse(null);
        int versionNumber = Math.toIntExact(boothReviewRequestRepository.countByBoothId(booth.getId()) + 1);
        BoothReviewSnapshot snapshot = snapshotFactory.create(booth);
        var changeSummary = diffService.buildSummary(snapshot, previousRequest, versionNumber);
        if (previousRequest != null
                && changeSummary.getComparisonCompleteness() == BoothReviewComparisonCompleteness.FULL
                && changeSummary.getTotalCount() == 0) {
            if (previousRequest.getStatus() == BoothReviewStatus.APPROVED) {
                booth.setStatus(BoothStatus.PUBLISHED);
                boothRepository.save(booth);
                return toSummary(previousRequest);
            }
            if (previousRequest.getStatus() == BoothReviewStatus.REJECTED) {
                throw new AppException(ErrorCode.BOOTH_REVIEW_NO_CHANGES_AFTER_REJECTION);
            }
        }
        BoothReviewRequest reviewRequest = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.PENDING)
                .submittedBy(currentUser)
                .versionNumber(versionNumber)
                .contentSnapshotJson(diffService.writeJson(snapshot))
                .changeSummaryJson(diffService.writeJson(changeSummary))
                .build();
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest savedRequest = boothReviewRequestRepository.save(reviewRequest);
        boothRepository.save(booth);
        return toSummary(savedRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getReviewHistory(
            User currentUser, UUID boothId, Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        return PageResponse.from(boothReviewRequestRepository
                .findByBoothIdOrderBySubmittedAtDesc(booth.getId(), pageable)
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getRequestsForOrganizer(
            User organizer,
            UUID exhibitionUuid,
            BoothReviewStatus status,
            String keyword,
            Pageable pageable) {
        assertAuthenticated(organizer);
        Page<BoothReviewRequestSummaryDTO> page = boothReviewRequestRepository
                .searchForOrganizer(organizer.getId(), exhibitionUuid, status, normalizeKeyword(keyword), pageable)
                .map(this::toSummary);
        return PageResponse.from(page);
    }

    @Transactional
    public BoothReviewRequestSummaryDTO approve(User organizer, UUID exhibitionUuid, UUID requestId) {
        exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        boothReviewPolicyService.assertCanReviewBooth(request.getBooth());
        assertPending(request);
        request.setStatus(BoothReviewStatus.APPROVED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(Instant.now(clock));
        request.setRejectedReason(null);
        request.getBooth().setStatus(BoothStatus.PUBLISHED);
        BoothReviewRequest saved = boothReviewRequestRepository.save(request);
        boothRepository.save(request.getBooth());
        BoothReviewRequestSummaryDTO summary = toSummary(saved);
        sendBoothReviewMailSafely(saved, BoothReviewStatus.APPROVED);
        return summary;
    }

    @Transactional
    public BoothReviewRequestSummaryDTO reject(
            User organizer,
            UUID exhibitionUuid,
            UUID requestId,
            RejectBoothReviewRequest rejectRequest) {
        exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        boothReviewPolicyService.assertCanReviewBooth(request.getBooth());
        assertPending(request);
        if (rejectRequest == null || rejectRequest.getRejectedReason() == null
                || rejectRequest.getRejectedReason().isBlank()) {
            throw new AppException(ErrorCode.BOOTH_REVIEW_REJECTION_REASON_REQUIRED);
        }
        request.setStatus(BoothReviewStatus.REJECTED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(Instant.now(clock));
        request.setRejectedReason(rejectRequest.getRejectedReason().trim());
        request.getBooth().setStatus(BoothStatus.DRAFT);
        BoothReviewRequest saved = boothReviewRequestRepository.save(request);
        boothRepository.save(request.getBooth());
        BoothReviewRequestSummaryDTO summary = toSummary(saved);
        sendBoothReviewMailSafely(saved, BoothReviewStatus.REJECTED);
        return summary;
    }

    private void sendBoothReviewMailSafely(BoothReviewRequest request, BoothReviewStatus result) {
        User recipient = request.getSubmittedBy();
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank())
            return;
        String email = recipient.getEmail();
        String fullName = recipient.getFullName();
        Booth booth = request.getBooth();
        String boothName = booth != null ? booth.getName() : "";
        String exhibitionName = "";
        if (booth != null && booth.getExhibitorRegistration() != null
                && booth.getExhibitorRegistration().getExhibitionPackage() != null
                && booth.getExhibitorRegistration().getExhibitionPackage().getExhibition() != null) {
            exhibitionName = booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().getName();
        }
        Integer versionNumber = request.getVersionNumber();
        String resultStr = result != null ? result.name() : null;
        String rejectedReason = request.getRejectedReason();
        Instant reviewedAt = request.getReviewedAt();
        String finalExhibitionName = exhibitionName;

        afterCommitExecutor.execute(() -> mailService.sendBoothReviewResultEmail(
                email,
                fullName,
                boothName,
                finalExhibitionName,
                versionNumber,
                resultStr,
                rejectedReason,
                reviewedAt));
    }

    @Transactional(readOnly = true)
    public OrganizerBoothContentOverviewDTO getContentOverviewForOrganizer(
            User organizer, UUID exhibitionUuid, UUID boothId) {
        Booth booth = getOrganizerBooth(organizer, exhibitionUuid, boothId);
        assertReviewVisibleStatus(booth);

        BoothReviewRequest pendingRequest = null;
        if (booth.getStatus() == BoothStatus.PENDING) {
            pendingRequest = boothReviewRequestRepository
                    .findTopByBoothIdAndStatusOrderBySubmittedAtDesc(booth.getId(), BoothReviewStatus.PENDING)
                    .orElseThrow(() -> new AppException(ErrorCode.BOOTH_REVIEW_REQUEST_NOT_FOUND));
        }
        return contentAssembler.toOrganizerContentOverview(booth, pendingRequest);
    }

    @Transactional(readOnly = true)
    public BoothResponseDTO getTourPreviewForOrganizer(User organizer, UUID exhibitionUuid, UUID boothId) {
        Booth booth = getOrganizerBooth(organizer, exhibitionUuid, boothId);
        assertReviewVisibleStatus(booth);
        return boothMapper.toBoothResponseDTO(booth);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getReviewHistoryForOrganizer(
            User organizer, UUID exhibitionUuid, UUID boothId, Pageable pageable) {
        Booth booth = getOrganizerBooth(organizer, exhibitionUuid, boothId);
        assertReviewVisibleStatus(booth);
        return PageResponse.from(boothReviewRequestRepository
                .findByBoothIdOrderBySubmittedAtDesc(booth.getId(), pageable)
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getBoothsForOrganizer(
            User organizer,
            UUID exhibitionUuid,
            String keyword,
            BoothStatus status,
            Pageable pageable) {
        assertAuthenticated(organizer);
        return PageResponse.from(boothRepository
                .searchForOrganizer(exhibitionUuid, organizer.getId(), normalizeKeyword(keyword), status, pageable)
                .map(boothMapper::toBoothResponseDTO));
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> countPendingBoothsGroupedByExhibition(List<Integer> exhibitionIds) {
        if (exhibitionIds == null || exhibitionIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> result = new HashMap<>();
        boothRepository.countBoothsGroupedByExhibitionAndStatus(exhibitionIds, BoothStatus.PENDING)
                .forEach(row -> result.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Object[]> countBoothsGroupedByStatus() {
        return boothRepository.countBoothsGroupedByStatus();
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> countBoothsGroupedByExhibition(List<Integer> exhibitionIds) {
        if (exhibitionIds == null || exhibitionIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> result = new HashMap<>();
        boothRepository.countBoothsGroupedByExhibition(exhibitionIds)
                .forEach(row -> result.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public long countBoothsByExhibitionId(Integer exhibitionId) {
        return boothRepository.countBoothsByExhibitionId(exhibitionId);
    }

    @Transactional(readOnly = true)
    public List<Booth> findBoothsByExhibitionId(Integer exhibitionId) {
        return boothRepository.findBoothsByExhibitionId(exhibitionId);
    }

    @Transactional(readOnly = true)
    public Booth findBoothEntityById(UUID boothId) {
        return boothRepository.findById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<Booth> findCompanyBooths(UUID companyId, Pageable pageable) {
        return boothRepository.findCompanyBooths(companyId, pageable);
    }

    private BoothReviewRequestSummaryDTO toSummary(BoothReviewRequest request) {
        return contentAssembler.toRequestSummary(
                request,
                diffService.readSummary(request.getChangeSummaryJson()));
    }

    private Booth getOrganizerBooth(User organizer, UUID exhibitionUuid, UUID boothId) {
        assertAuthenticated(organizer);
        return boothRepository.findDetailForOrganizer(boothId, exhibitionUuid, organizer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private void assertReviewVisibleStatus(Booth booth) {
        if (booth.getStatus() != BoothStatus.PENDING
                && booth.getStatus() != BoothStatus.PUBLISHED
                && booth.getStatus() != BoothStatus.ARCHIVED) {
            throw new AppException(ErrorCode.INVALID_BOOTH_REVIEW_STATUS);
        }
    }

    private void assertPending(BoothReviewRequest request) {
        if (request.getStatus() != BoothReviewStatus.PENDING
                || request.getBooth().getStatus() != BoothStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOTH_REVIEW_STATUS);
        }
    }

    private void assertAuthenticated(User user) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private Booth getBoothForCompany(UUID boothId, Company company) {
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private Exhibition getExhibition(Booth booth) {
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        if (exhibitionPackage == null) {
            throw new AppException(ErrorCode.REGISTRATION_PACKAGE_MISSING);
        }
        if (exhibitionPackage.getExhibition() == null) {
            throw new AppException(ErrorCode.REGISTRATION_EXHIBITION_MISSING);
        }
        return exhibitionPackage.getExhibition();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
