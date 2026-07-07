package com.example.vex360.features.booth.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestDetailDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
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
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothReviewService {
    private final BoothRepository boothRepository;
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final CompanyService companyService;
    private final BoothMapper boothMapper;
    private final BoothReviewPolicyService boothReviewPolicyService;

    @Transactional
    public BoothResponseDTO startEdit(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        if (booth.getStatus() != BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.INVALID_BOOTH_REVIEW_STATUS);
        }
        boothReviewPolicyService.assertBeforeReviewDeadline(booth);
        booth.setStatus(BoothStatus.DRAFT);
        return boothMapper.toBoothResponseDTO(boothRepository.save(booth));
    }

    @Transactional
    public BoothReviewRequestDetailDTO submitReview(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        boothReviewPolicyService.assertCanSubmitReview(booth);

        BoothReviewRequest reviewRequest = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.PENDING)
                .submittedBy(currentUser)
                .build();
        booth.setStatus(BoothStatus.PENDING);

        BoothReviewRequest savedRequest = boothReviewRequestRepository.save(reviewRequest);
        Booth savedBooth = boothRepository.save(booth);
        return toDetail(savedRequest, savedBooth);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getReviewHistory(
            User currentUser,
            UUID boothId,
            Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        Page<BoothReviewRequestSummaryDTO> page = boothReviewRequestRepository
                .findByBoothIdOrderBySubmittedAtDesc(booth.getId(), pageable)
                .map(this::toSummary);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getRequestsForOrganizer(
            User organizer,
            UUID exhibitionUuid,
            BoothReviewStatus status,
            String keyword,
            Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Page<BoothReviewRequestSummaryDTO> page = boothReviewRequestRepository
                .searchForOrganizer(organizer.getId(), exhibitionUuid, status, normalizeKeyword(keyword), pageable)
                .map(this::toSummary);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public BoothReviewRequestDetailDTO getRequestForOrganizer(User organizer, UUID exhibitionUuid, UUID requestId) {
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        return toDetail(request, request.getBooth());
    }

    @Transactional
    public BoothReviewRequestDetailDTO approve(User organizer, UUID exhibitionUuid, UUID requestId) {
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        if (request.getStatus() != BoothReviewStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOTH_REVIEW_STATUS);
        }

        request.setStatus(BoothReviewStatus.APPROVED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectedReason(null);
        request.getBooth().setStatus(BoothStatus.PUBLISHED);

        BoothReviewRequest savedRequest = boothReviewRequestRepository.save(request);
        Booth savedBooth = boothRepository.save(request.getBooth());
        return toDetail(savedRequest, savedBooth);
    }

    @Transactional
    public BoothReviewRequestDetailDTO reject(
            User organizer,
            UUID exhibitionUuid,
            UUID requestId,
            RejectBoothReviewRequest rejectRequest) {
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        if (request.getStatus() != BoothReviewStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_BOOTH_REVIEW_STATUS);
        }
        if (rejectRequest == null || rejectRequest.getRejectedReason() == null
                || rejectRequest.getRejectedReason().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        request.setStatus(BoothReviewStatus.REJECTED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectedReason(rejectRequest.getRejectedReason().trim());
        request.getBooth().setStatus(BoothStatus.DRAFT);

        BoothReviewRequest savedRequest = boothReviewRequestRepository.save(request);
        Booth savedBooth = boothRepository.save(request.getBooth());
        return toDetail(savedRequest, savedBooth);
    }

    private BoothReviewRequestDetailDTO toDetail(BoothReviewRequest request, Booth booth) {
        return BoothReviewRequestDetailDTO.builder()
                .request(toSummary(request))
                .booth(boothMapper.toBoothResponseDTO(booth))
                .build();
    }

    private BoothReviewRequestSummaryDTO toSummary(BoothReviewRequest request) {
        Booth booth = request.getBooth();
        Company company = booth.getCompany();
        Exhibition exhibition = getExhibition(booth);
        return BoothReviewRequestSummaryDTO.builder()
                .id(request.getId())
                .boothId(booth.getId())
                .boothName(booth.getName())
                .companyId(company == null ? null : company.getId())
                .companyName(company == null ? null : company.getName())
                .exhibitionUuid(exhibition == null ? null : exhibition.getUuid())
                .exhibitionName(exhibition == null ? null : exhibition.getName())
                .status(request.getStatus())
                .submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt())
                .rejectedReason(request.getRejectedReason())
                .build();
    }

    private Exhibition getExhibition(Booth booth) {
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null) {
            return null;
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        return exhibitionPackage == null ? null : exhibitionPackage.getExhibition();
    }

    private Booth getBoothForCompany(UUID boothId, Company company) {
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
