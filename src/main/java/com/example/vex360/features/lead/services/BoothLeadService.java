package com.example.vex360.features.lead.services;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.lead.dtos.request.CreateBoothLeadRequest;
import com.example.vex360.features.lead.dtos.request.UpdateBoothLeadRequest;
import com.example.vex360.features.lead.dtos.response.BoothLeadResponseDTO;
import com.example.vex360.features.lead.dtos.response.BoothLeadSubmissionResponseDTO;
import com.example.vex360.features.lead.dtos.response.BoothLeadSubmissionStatusDTO;
import com.example.vex360.features.lead.dtos.response.BoothLeadSummaryDTO;
import com.example.vex360.features.lead.entities.BoothLead;
import com.example.vex360.shared.enums.LeadStatus;
import com.example.vex360.features.lead.repositories.BoothLeadRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoothLeadService {

    private final BoothLeadRepository boothLeadRepository;
    private final BoothRepository boothRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public BoothLeadSubmissionResponseDTO submitLead(
            User visitor,
            UUID exhibitionUuid,
            UUID boothId,
            CreateBoothLeadRequest request) {
        requireAuthenticated(visitor);

        Exhibition exhibition = exhibitionRepository.findByUuid(exhibitionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));
        if (exhibition.getStatus() != ExhibitionStatus.ACTIVE) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        Booth booth = boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibitionUuid,
                boothId,
                BoothStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));

        BoothLead lead = boothLeadRepository.findByBoothIdAndVisitorId(boothId, visitor.getId())
                .orElse(null);
        boolean alreadySubmitted = lead != null;
        Instant consentAt = Instant.now();

        if (lead == null) {
            lead = BoothLead.builder()
                    .booth(booth)
                    .visitor(visitor)
                    .status(LeadStatus.NEW)
                    .consentAt(consentAt)
                    .build();
        }

        lead.setFullName(request.fullName().trim());
        lead.setEmail(request.email().trim().toLowerCase());
        lead.setPhoneNumber(trimToNull(request.phoneNumber()));
        lead.setCompanyName(trimToNull(request.companyName()));
        lead.setMessage(trimToNull(request.message()));
        lead.setConsentAt(consentAt);

        BoothLead saved = boothLeadRepository.save(lead);
        return new BoothLeadSubmissionResponseDTO(
                saved.getId(),
                alreadySubmitted,
                alreadySubmitted ? saved.getCreatedAt() : consentAt);
    }

    @Transactional(readOnly = true)
    public BoothLeadSubmissionStatusDTO getSubmissionStatus(
            User visitor,
            UUID exhibitionUuid,
            UUID boothId) {
        requireAuthenticated(visitor);
        Booth booth = boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibitionUuid,
                boothId,
                BoothStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));

        return boothLeadRepository.findByBoothIdAndVisitorId(booth.getId(), visitor.getId())
                .map(lead -> new BoothLeadSubmissionStatusDTO(true, lead.getCreatedAt()))
                .orElseGet(() -> new BoothLeadSubmissionStatusDTO(false, null));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothLeadResponseDTO> getLeads(
            User exhibitor,
            UUID boothId,
            LeadStatus status,
            String keyword,
            Pageable pageable) {
        Company company = getCompany(exhibitor);
        if (boothId != null) {
            requireOwnedBooth(company.getId(), boothId);
        }

        return PageResponse.from(boothLeadRepository.searchForCompany(
                company.getId(),
                boothId,
                status,
                trimToNull(keyword),
                pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BoothLeadSummaryDTO getSummary(User exhibitor, UUID boothId) {
        Company company = getCompany(exhibitor);
        if (boothId != null) {
            requireOwnedBooth(company.getId(), boothId);
        }

        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        boothLeadRepository.countByStatusForCompany(company.getId(), boothId)
                .forEach(row -> counts.put((LeadStatus) row[0], (Long) row[1]));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        return new BoothLeadSummaryDTO(
                total,
                counts.getOrDefault(LeadStatus.NEW, 0L),
                counts.getOrDefault(LeadStatus.CONTACTED, 0L),
                counts.getOrDefault(LeadStatus.QUALIFIED, 0L),
                counts.getOrDefault(LeadStatus.CONVERTED, 0L),
                counts.getOrDefault(LeadStatus.LOST, 0L));
    }

    @Transactional
    public BoothLeadResponseDTO updateLead(
            User exhibitor,
            UUID leadId,
            UpdateBoothLeadRequest request) {
        Company company = getCompany(exhibitor);
        BoothLead lead = boothLeadRepository.findOwnedLead(leadId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.LEAD_NOT_FOUND));

        lead.setStatus(request.status());
        lead.setExhibitorNote(trimToNull(request.exhibitorNote()));
        return toResponse(boothLeadRepository.save(lead));
    }

    private Company getCompany(User exhibitor) {
        requireAuthenticated(exhibitor);
        return companyRepository.findByOwnerUserId(exhibitor.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private void requireOwnedBooth(UUID companyId, UUID boothId) {
        boothRepository.findCompanyBoothById(boothId, companyId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private BoothLeadResponseDTO toResponse(BoothLead lead) {
        Booth booth = lead.getBooth();
        Exhibition exhibition = booth.getExhibitorRegistration() == null
                || booth.getExhibitorRegistration().getExhibitionPackage() == null
                        ? null
                        : booth.getExhibitorRegistration().getExhibitionPackage().getExhibition();

        return new BoothLeadResponseDTO(
                lead.getId(),
                booth.getId(),
                booth.getName(),
                exhibition == null ? null : exhibition.getUuid(),
                exhibition == null ? null : exhibition.getName(),
                lead.getVisitor() == null ? null : lead.getVisitor().getId(),
                lead.getVisitor() == null ? null : lead.getVisitor().getAvatarUrl(),
                lead.getFullName(),
                lead.getEmail(),
                lead.getPhoneNumber(),
                lead.getCompanyName(),
                lead.getMessage(),
                lead.getStatus(),
                lead.getExhibitorNote(),
                lead.getConsentAt(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }

    private void requireAuthenticated(User user) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
