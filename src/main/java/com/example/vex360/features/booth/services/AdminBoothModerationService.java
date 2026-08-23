package com.example.vex360.features.booth.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.BanBoothRequest;
import com.example.vex360.features.booth.dtos.request.WarnBoothRequest;
import com.example.vex360.features.booth.dtos.response.AdminBoothContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothModerationSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBoothModerationService {
    private final BoothRepository boothRepository;
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final BoothReviewContentAssembler contentAssembler;
    private final ExhibitionService exhibitionService;
    private final ExhibitionParticipationPolicy participationPolicy;
    private final MailService mailService;
    private final BoothMapper boothMapper;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getBoothsForAdmin(
            User admin,
            UUID exhibitionUuid,
            String keyword,
            BoothStatus status,
            Pageable pageable) {
        assertAdmin(admin);
        exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid);
        String normKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<BoothResponseDTO> page = boothRepository
                .searchForAdmin(exhibitionUuid, normKeyword, status, pageable)
                .map(boothMapper::toBoothResponseDTO);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothModerationSummaryDTO> getModerationSummary(
            User admin, String keyword, Pageable pageable) {
        assertAdmin(admin);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return PageResponse.from(boothRepository
                .searchModeratedForAdmin(normalizedKeyword, pageable)
                .map(this::toModerationSummary));
    }

    @Transactional(readOnly = true)
    public AdminBoothContentOverviewDTO getContentOverviewForAdmin(
            User admin,
            UUID boothId) {
        assertAdmin(admin);
        Booth booth = boothRepository.findDetailForAdmin(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));

        BoothResponseDTO boothDTO = boothMapper.toBoothResponseDTO(booth);
        BoothReviewContentOverviewDTO contentOverview = contentAssembler.toContentOverview(booth);

        boolean canWarn = boothReviewPolicyService.isBoothWarnAllowed(booth);
        boolean canBan = boothReviewPolicyService.isBoothBanAllowed(booth);

        String cannotWarnReason = null;
        if (!canWarn) {
            if (booth.getStatus() == BoothStatus.BANNED) {
                cannotWarnReason = "Gian hàng đã bị khóa (BANNED).";
            } else if (booth.getWarningCount() != null && booth.getWarningCount() >= 1) {
                cannotWarnReason = "Gian hàng đã bị cảnh báo 1 lần trước đó. Không thể gửi cảnh báo thêm.";
            } else {
                cannotWarnReason = "Từ mốc T-3 trở đi hoặc khi triển lãm đã diễn ra, hệ thống chỉ hỗ trợ thực hiện BAN.";
            }
        }

        AdminBoothContentOverviewDTO.AdminBoothModerationDTO moderation = AdminBoothContentOverviewDTO.AdminBoothModerationDTO
                .builder()
                .status(booth.getStatus())
                .warningCount(booth.getWarningCount() == null ? 0 : booth.getWarningCount())
                .warningReason(booth.getWarningReason())
                .warnedAt(booth.getWarnedAt())
                .warnedById(booth.getWarnedBy() == null ? null : booth.getWarnedBy().getId())
                .warnedByName(booth.getWarnedBy() == null ? null : booth.getWarnedBy().getFullName())
                .banReason(booth.getBanReason())
                .bannedAt(booth.getBannedAt())
                .bannedById(booth.getBannedBy() == null ? null : booth.getBannedBy().getId())
                .bannedByName(booth.getBannedBy() == null ? null : booth.getBannedBy().getFullName())
                .canWarn(canWarn)
                .canBan(canBan)
                .cannotWarnReason(cannotWarnReason)
                .build();

        return AdminBoothContentOverviewDTO.builder()
                .booth(boothDTO)
                .contentOverview(contentOverview)
                .moderation(moderation)
                .build();
    }

    @Transactional
    public BoothResponseDTO warnBooth(User admin, UUID exhibitionUuid, UUID boothId, WarnBoothRequest request) {
        assertAdmin(admin);
        if (request == null || request.getWarningReason() == null || request.getWarningReason().isBlank()) {
            throw new AppException(ErrorCode.BOOTH_WARNING_REASON_REQUIRED);
        }
        exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        Booth booth = boothRepository.findDetailForAdmin(boothId, exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        participationPolicy.assertSupportsParticipation(
                booth.getExhibitorRegistration().getExhibitionPackage().getExhibition());

        boothReviewPolicyService.assertCanWarnBooth(booth);

        Instant now = Instant.now(clock);
        booth.setStatus(BoothStatus.DRAFT);
        booth.setWarningCount((booth.getWarningCount() == null ? 0 : booth.getWarningCount()) + 1);
        booth.setWarningReason(request.getWarningReason().trim());
        booth.setWarnedAt(now);
        booth.setWarnedBy(admin);

        boothReviewRequestRepository
                .findTopByBoothIdAndStatusOrderBySubmittedAtDesc(booth.getId(), BoothReviewStatus.PENDING)
                .ifPresent(pendingReq -> {
                    pendingReq.setStatus(BoothReviewStatus.CANCELED);
                    pendingReq.setCanceledAt(now);
                    pendingReq.setCancellationReason("Admin issued warning: " + request.getWarningReason().trim());
                    boothReviewRequestRepository.save(pendingReq);
                });

        Booth saved = boothRepository.save(booth);
        User recipient = getRecipient(saved);
        if (recipient != null && recipient.getEmail() != null) {
            afterCommitExecutor.execute(() -> mailService.sendBoothWarningEmail(
                    recipient.getEmail(), recipient.getFullName(), saved.getName(), getExhibitionName(saved),
                    saved.getWarningReason(), now));
        }

        return boothMapper.toBoothResponseDTO(saved);
    }

    @Transactional
    public BoothResponseDTO banBooth(User admin, UUID exhibitionUuid, UUID boothId, BanBoothRequest request) {
        assertAdmin(admin);
        if (request == null || request.getBanReason() == null || request.getBanReason().isBlank()) {
            throw new AppException(ErrorCode.BOOTH_BAN_REASON_REQUIRED);
        }
        exhibitionService.findExhibitionForUpdate(exhibitionUuid);
        Booth booth = boothRepository.findDetailForAdmin(boothId, exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        participationPolicy.assertSupportsParticipation(
                booth.getExhibitorRegistration().getExhibitionPackage().getExhibition());

        boothReviewPolicyService.assertCanBanBooth(booth);

        Instant now = Instant.now(clock);
        booth.setStatus(BoothStatus.BANNED);
        booth.setBanReason(request.getBanReason().trim());
        booth.setBannedAt(now);
        booth.setBannedBy(admin);

        boothReviewRequestRepository
                .findTopByBoothIdAndStatusOrderBySubmittedAtDesc(booth.getId(), BoothReviewStatus.PENDING)
                .ifPresent(pendingReq -> {
                    pendingReq.setStatus(BoothReviewStatus.CANCELED);
                    pendingReq.setCanceledAt(now);
                    pendingReq.setCancellationReason("Admin banned booth: " + request.getBanReason().trim());
                    boothReviewRequestRepository.save(pendingReq);
                });

        Booth saved = boothRepository.save(booth);
        User recipient = getRecipient(saved);
        if (recipient != null && recipient.getEmail() != null) {
            afterCommitExecutor.execute(() -> mailService.sendBoothBanEmail(
                    recipient.getEmail(), recipient.getFullName(), saved.getName(), getExhibitionName(saved),
                    saved.getBanReason(), now));
        }

        return boothMapper.toBoothResponseDTO(saved);
    }

    private void assertAdmin(User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private BoothModerationSummaryDTO toModerationSummary(Booth booth) {
        boolean latestIsBan = booth.getBannedAt() != null
                && (booth.getWarnedAt() == null || !booth.getBannedAt().isBefore(booth.getWarnedAt()));
        var exhibition = booth.getExhibitorRegistration().getExhibitionPackage().getExhibition();
        return BoothModerationSummaryDTO.builder()
                .boothId(booth.getId())
                .boothName(booth.getName())
                .exhibitionId(exhibition.getUuid())
                .exhibitionName(exhibition.getName())
                .companyName(booth.getCompany() == null ? null : booth.getCompany().getName())
                .boothStatus(booth.getStatus())
                .warningCount(booth.getWarningCount() == null ? 0 : booth.getWarningCount())
                .warningReason(booth.getWarningReason())
                .warnedAt(booth.getWarnedAt())
                .banReason(booth.getBanReason())
                .bannedAt(booth.getBannedAt())
                .latestAction(latestIsBan ? "BAN" : "WARNING")
                .latestReason(latestIsBan ? booth.getBanReason() : booth.getWarningReason())
                .latestActionAt(latestIsBan ? booth.getBannedAt() : booth.getWarnedAt())
                .build();
    }

    private User getRecipient(Booth booth) {
        if (booth.getCreatedBy() != null) {
            return booth.getCreatedBy();
        }
        if (booth.getCompany() != null) {
            return booth.getCompany().getOwnerUser();
        }
        return null;
    }

    private String getExhibitionName(Booth booth) {
        if (booth.getExhibitorRegistration() != null
                && booth.getExhibitorRegistration().getExhibitionPackage() != null
                && booth.getExhibitorRegistration().getExhibitionPackage().getExhibition() != null) {
            return booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().getName();
        }
        return "";
    }
}
