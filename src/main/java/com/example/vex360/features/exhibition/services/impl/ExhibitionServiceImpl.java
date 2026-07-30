package com.example.vex360.features.exhibition.services.impl;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;

import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.AdminExhibitionStatusFilter;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.OrganizerExhibitionSummaryItemResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.OrganizerExhibitionSummaryResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.utils.PageableUtils;

import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.ExhibitionReviewHistoryService;
import com.example.vex360.features.exhibition.enums.ExhibitionReviewStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionServiceImpl implements ExhibitionService {

    private static final int MAX_SPONSORS = 15;
    private static final int MAX_EXHIBITION_DURATION_DAYS = 90;
    private static final List<ExhibitorRegistrationStatus> ACTION_REQUIRED_REGISTRATION_STATUSES = List.of(
            ExhibitorRegistrationStatus.PENDING,
            ExhibitorRegistrationStatus.PENDING_PAYMENT);

    private static final Map<String, String> ADMIN_SORT_ALIASES = Map.of(
            "organizerName", "organizer.fullName",
            "exhibitionName", "name",
            "expectedBoothCount", "estimatedBooths",
            "status", "status");

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionPackageRepository exhibitionPackageRepository;
    private final PackageTemplateService packageTemplateService;
    private final ExhibitionAssetRepository exhibitionAssetRepository;
    private final ExhibitorRegistrationRepository exhibitorRegistrationRepository;
    private final BoothRepository boothRepository;
    private final CompanyRepository companyRepository;
    private final ExhibitionMapper exhibitionMapper;
    private final CloudService cloudService;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final ExhibitionTimelinePolicy timelinePolicy;
    private final UserRepository userRepository;
    private final ExhibitionReviewHistoryService reviewHistoryService;

    @Override
    @Transactional
    public ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request,
            MultipartFile keyVisual, List<MultipartFile> sponsorLogos) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        validateImageFile(keyVisual, true);

        // Validate sponsor logos and names if provided
        int sponsorLogoCount = sponsorLogos != null ? sponsorLogos.size() : 0;
        int sponsorRequestCount = request.getSponsors() != null ? request.getSponsors().size() : 0;

        if (sponsorLogoCount > MAX_SPONSORS || sponsorRequestCount > MAX_SPONSORS) {
            log.error("Exhibition sponsors size exceeds limit of {}", MAX_SPONSORS);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (sponsorLogoCount != sponsorRequestCount) {
            log.error("Sponsor logos count {} does not match sponsor names count {}", sponsorLogoCount,
                    sponsorRequestCount);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (sponsorLogos != null && !sponsorLogos.isEmpty()) {
            for (MultipartFile logo : sponsorLogos) {
                validateImageFile(logo, true);
            }
        }

        // 1. Lock organizer row to prevent race conditions on pending count
        userRepository.findByIdForUpdate(organizer.getId());

        // Validate dates & max duration
        if (request.getEndDate().isBefore(request.getStartDate())) {
            log.error("Exhibition end date {} cannot be before start date {}", request.getEndDate(),
                    request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) > MAX_EXHIBITION_DURATION_DAYS) {
            log.error("Exhibition duration from {} to {} exceeds maximum allowed limit of {} days",
                    request.getStartDate(), request.getEndDate(), MAX_EXHIBITION_DURATION_DAYS);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (!timelinePolicy.hasMinimumLeadTime(request.getStartDate())) {
            log.error("Exhibition start date {} does not meet minimum lead time requirement", request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        long pendingCount = exhibitionRepository.countByOrganizerIdAndStatus(organizer.getId(),
                ExhibitionStatus.PENDING);
        if (pendingCount >= 3) {
            log.error("Organizer {} already has {} pending exhibition requests", organizer.getId(), pendingCount);
            throw new AppException(ErrorCode.EXHIBITION_LIMIT_EXCEEDED);
        }

        if (exhibitionRepository.existsByNameIgnoreCase(request.getName().trim())) {
            log.error("Exhibition name '{}' already exists", request.getName().trim());
            throw new AppException(ErrorCode.EXHIBITION_NAME_DUPLICATED);
        }

        // Pre-validate packages
        if (request.getPackages() != null && !request.getPackages().isEmpty()) {
            if (request.getPackages().size() > 3) {
                log.error("Exhibition packages size exceeds limit of 3");
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }

            Set<BoothListingPriority> priorities = new HashSet<>();
            for (ConfigureExhibitionPackageRequest pkgReq : request.getPackages()) {
                PackageTemplate template = packageTemplateService.getPackageTemplateEntity(pkgReq.getTemplateId());

                if (pkgReq.getFinalPrice().compareTo(template.getPrice()) < 0) {
                    log.error("Package final price {} is below floor price {}", pkgReq.getFinalPrice(),
                            template.getPrice());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }

                if (!priorities.add(template.getListingPriority())) {
                    log.error("Duplicate package priority type {} is not allowed", template.getListingPriority());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }
            }
        }

        // Save exhibition entity after all validations pass
        Exhibition exhibition = Exhibition.builder()
                .organizer(organizer)
                .uuid(UUID.randomUUID())
                .name(request.getName().trim())
                .category(request.getCategory().trim())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .estimatedBooths(request.getEstimatedBooths())
                .status(ExhibitionStatus.PENDING)
                .build();

        exhibition = exhibitionRepository.save(exhibition);
        log.info("Created Exhibition: {} (ID: {}, UUID: {})", exhibition.getName(), exhibition.getId(),
                exhibition.getUuid());

        // Upload keyVisual and save as ExhibitionAsset
        CloudinaryResponse uploadRes = cloudService.upload(keyVisual);
        deleteCloudAssetOnRollback(uploadRes.getPublicId(), "image");
        ExhibitionAsset keyVisualAsset = ExhibitionAsset.builder()
                .exhibition(exhibition)
                .assetUrl(uploadRes.getUrl())
                .publicId(uploadRes.getPublicId())
                .type(ExhibitionAssetType.KEY_VISUAL)
                .build();
        exhibitionAssetRepository.save(keyVisualAsset);
        exhibition.getAssets().add(keyVisualAsset);

        // Save packages
        List<ExhibitionPackage> savedPackages = new ArrayList<>();
        if (request.getPackages() != null && !request.getPackages().isEmpty()) {
            for (ConfigureExhibitionPackageRequest pkgReq : request.getPackages()) {
                PackageTemplate template = packageTemplateService.getPackageTemplateEntity(pkgReq.getTemplateId());

                ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                        .exhibition(exhibition)
                        .template(template)
                        .finalPrice(pkgReq.getFinalPrice())
                        .status(ExhibitionPackageStatus.ACTIVE)
                        .build();

                savedPackages.add(exhibitionPackageRepository.save(exhibitionPackage));
            }
        }

        // Upload sponsor logos and save as ExhibitionAsset
        if (sponsorLogos != null && !sponsorLogos.isEmpty()) {
            for (int i = 0; i < sponsorLogos.size(); i++) {
                MultipartFile logo = sponsorLogos.get(i);
                if (logo != null && !logo.isEmpty()) {
                    String sponsorName = (request.getSponsors() != null && i < request.getSponsors().size())
                            ? request.getSponsors().get(i).getName()
                            : null;
                    CloudinaryResponse uploadResLogo = cloudService.upload(logo);
                    deleteCloudAssetOnRollback(uploadResLogo.getPublicId(), "image");
                    ExhibitionAsset sponsorLogoAsset = ExhibitionAsset.builder()
                            .exhibition(exhibition)
                            .name(sponsorName)
                            .assetUrl(uploadResLogo.getUrl())
                            .publicId(uploadResLogo.getPublicId())
                            .type(ExhibitionAssetType.SPONSOR_LOGO)
                            .build();
                    exhibitionAssetRepository.save(sponsorLogoAsset);
                    exhibition.getAssets().add(sponsorLogoAsset);
                }
            }
        }

        reviewHistoryService.recordInitialSubmission(exhibition, organizer,
                uploadRes != null ? uploadRes.getUrl() : null);

        return exhibitionMapper.toResponse(exhibition, savedPackages);
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionByUuid(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (exhibition.getStatus() != ExhibitionStatus.PUBLISHED
                && exhibition.getStatus() != ExhibitionStatus.ACTIVE
                && exhibition.getStatus() != ExhibitionStatus.COMPLETED) {
            log.error("Exhibition {} is not in public status (status: {})", uuid, exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
        }

        long visitorCount = analyticsEventRepository.countByExhibitionIdAndEventType(
                exhibition.getId(), AnalyticsEventType.ENTER_EXHIBITION);

        return exhibitionMapper.toPublicResponse(exhibition, null).toBuilder()
                .visitorCount(visitorCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Exhibition getExhibitionEntityById(Integer id) {
        return exhibitionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for ID: {}", id);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitionResponseDTO> searchExhibitionsForAdmin(
            String keyword, AdminExhibitionStatusFilter status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();

        List<ExhibitionStatus> statuses;
        if (status == null) {
            statuses = List.of(ExhibitionStatus.values());
        } else if (status == AdminExhibitionStatusFilter.APPROVED) {
            statuses = List.of(
                    ExhibitionStatus.REGISTRATION,
                    ExhibitionStatus.PUBLISHED,
                    ExhibitionStatus.ACTIVE,
                    ExhibitionStatus.COMPLETED);
        } else {
            statuses = List.of(ExhibitionStatus.valueOf(status.name()));
        }

        Pageable mappedPageable = PageableUtils.remapSort(pageable, ADMIN_SORT_ALIASES);
        Page<ExhibitionResponseDTO> exhibitions = exhibitionRepository.searchAdminExhibitions(
                normalizedKeyword, statuses, normalizedCategory, startDate, endDate, mappedPageable)
                .map(exhibitionMapper::toResponse);

        return PageResponse.from(exhibitions);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingExhibitions() {
        return exhibitionRepository.countByStatus(ExhibitionStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionDetailForAdmin(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        ExhibitionResponseDTO response = exhibitionMapper.toResponse(exhibition, packages);
        return companyRepository.findByOwnerUserId(exhibition.getOrganizer().getId())
                .map(company -> response.toBuilder()
                        .organizationName(company.getName())
                        .email(company.getEmail())
                        .phone(company.getPhone())
                        .build())
                .orElse(response);
    }

    @Override
    @Transactional
    public ExhibitionPackageResponseDTO configureExhibitionPackage(User organizer, UUID uuid,
            ConfigureExhibitionPackageRequest request) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        PackageTemplate template = packageTemplateService.getPackageTemplateEntity(request.getTemplateId());

        if (request.getFinalPrice().compareTo(template.getPrice()) < 0) {
            log.error("Package final price {} is below floor price {}", request.getFinalPrice(), template.getPrice());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (exhibitionPackageRepository.findByExhibitionIdAndTemplateId(exhibition.getId(), template.getId())
                .isPresent()) {
            log.error("Package template {} already configured for exhibition {}", template.getId(), uuid);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .exhibition(exhibition)
                .template(template)
                .finalPrice(request.getFinalPrice())
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();

        exhibitionPackage = exhibitionPackageRepository.save(exhibitionPackage);
        return exhibitionMapper.toPackageResponse(exhibitionPackage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitionResponseDTO> searchExhibitionsForOrganizer(
            User organizer, String keyword, ExhibitionStatus status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();

        Page<ExhibitionResponseDTO> exhibitions = exhibitionRepository.searchOrganizerExhibitions(
                organizer.getId(), normalizedKeyword, status, normalizedCategory, startDate, endDate, pageable)
                .map(exhibitionMapper::toResponse);

        return PageResponse.from(exhibitions);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerExhibitionSummaryResponseDTO getSummaryForOrganizer(User organizer) {
        List<OrganizerExhibitionSummaryItemResponseDTO> summaries = getSummariesByExhibitionForOrganizer(organizer);
        long pendingRegistrationCount = 0;
        long pendingBoothReviewCount = 0;
        for (OrganizerExhibitionSummaryItemResponseDTO summary : summaries) {
            pendingRegistrationCount += summary.getPendingRegistrationCount();
            pendingBoothReviewCount += summary.getPendingBoothReviewCount();
        }
        return new OrganizerExhibitionSummaryResponseDTO(
                pendingRegistrationCount,
                pendingBoothReviewCount,
                pendingRegistrationCount + pendingBoothReviewCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizerExhibitionSummaryItemResponseDTO> getSummariesByExhibitionForOrganizer(User organizer) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        List<Exhibition> exhibitions = exhibitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId());
        if (exhibitions.isEmpty()) {
            return List.of();
        }

        List<Integer> exhibitionIds = exhibitions.stream().map(Exhibition::getId).toList();
        Map<Integer, Long> registrationCounts = getRegistrationCounts(exhibitionIds);
        Map<Integer, Long> boothReviewCounts = getBoothReviewCounts(exhibitionIds);

        List<OrganizerExhibitionSummaryItemResponseDTO> summaries = new ArrayList<>(exhibitions.size());
        for (Exhibition exhibition : exhibitions) {
            long pendingRegistrationCount = registrationCounts.getOrDefault(exhibition.getId(), 0L);
            long pendingBoothReviewCount = boothReviewCounts.getOrDefault(exhibition.getId(), 0L);
            summaries.add(new OrganizerExhibitionSummaryItemResponseDTO(
                    exhibition.getUuid(),
                    exhibition.getName(),
                    pendingRegistrationCount,
                    pendingBoothReviewCount,
                    pendingRegistrationCount + pendingBoothReviewCount));
        }
        return summaries;
    }

    private Map<Integer, Long> getRegistrationCounts(List<Integer> exhibitionIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : exhibitorRegistrationRepository.countActionRequiredGroupedByExhibition(
                exhibitionIds, ACTION_REQUIRED_REGISTRATION_STATUSES)) {
            counts.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Map<Integer, Long> getBoothReviewCounts(List<Integer> exhibitionIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : boothRepository.countBoothsGroupedByExhibitionAndStatus(
                exhibitionIds, BoothStatus.PENDING)) {
            counts.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionDetailForOrganizer(User organizer, UUID uuid) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO updateExhibitionForOrganizer(User organizer, UUID uuid,
            CreateExhibitionRequest request, MultipartFile keyVisual) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Allow update only if PENDING or REJECTED
        if (exhibition.getStatus() != ExhibitionStatus.PENDING && exhibition.getStatus() != ExhibitionStatus.REJECTED) {
            log.error("Exhibition {} status is not PENDING or REJECTED. Cannot update.", exhibition.getId());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        // Rejection limit validation
        if (exhibition.getRejectionCount() >= 3) {
            log.error("Exhibition {} has reached the maximum rejection limit (3). Cannot edit.", exhibition.getId());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        // Date validation: endDate >= startDate
        if (request.getEndDate().isBefore(request.getStartDate())) {
            log.error("Exhibition end date {} cannot be before start date {}", request.getEndDate(),
                    request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) > MAX_EXHIBITION_DURATION_DAYS) {
            log.error("Exhibition duration from {} to {} exceeds maximum allowed limit of {} days",
                    request.getStartDate(), request.getEndDate(), MAX_EXHIBITION_DURATION_DAYS);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (!timelinePolicy.hasMinimumLeadTime(request.getStartDate())) {
            log.error("Exhibition start date {} does not meet minimum lead time requirement", request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        // Update time-window validation: must be before start date, and no exhibitors
        // registered yet
        if (!LocalDate.now().isBefore(exhibition.getStartDate())) {
            log.error("Cannot update exhibition on or after its start date {}", exhibition.getStartDate());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageExhibitionId(exhibition.getId())) {
            log.error("Cannot update exhibition because exhibitors have already registered");
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        String trimmedName = request.getName().trim();
        if (!exhibition.getName().equalsIgnoreCase(trimmedName)
                && exhibitionRepository.existsByNameIgnoreCase(trimmedName)) {
            log.error("Exhibition name '{}' already exists", trimmedName);
            throw new AppException(ErrorCode.EXHIBITION_NAME_DUPLICATED);
        }

        // Pre-validate packages
        if (request.getPackages() != null && !request.getPackages().isEmpty()) {
            if (request.getPackages().size() > 3) {
                log.error("Exhibition packages size exceeds limit of 3");
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }

            Set<BoothListingPriority> priorities = new HashSet<>();
            for (ConfigureExhibitionPackageRequest pkgReq : request.getPackages()) {
                PackageTemplate template = packageTemplateService.getPackageTemplateEntity(pkgReq.getTemplateId());

                if (pkgReq.getFinalPrice().compareTo(template.getPrice()) < 0) {
                    log.error("Package final price {} is below floor price {}", pkgReq.getFinalPrice(),
                            template.getPrice());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }

                if (!priorities.add(template.getListingPriority())) {
                    log.error("Duplicate package priority type {} is not allowed", template.getListingPriority());
                    throw new AppException(ErrorCode.VALIDATION_FAILED);
                }
            }
        }

        // Update basic metadata
        exhibition.setName(trimmedName);
        exhibition.setCategory(request.getCategory().trim());
        exhibition.setDescription(request.getDescription());
        exhibition.setStartDate(request.getStartDate());
        exhibition.setEndDate(request.getEndDate());
        exhibition.setEstimatedBooths(request.getEstimatedBooths());

        // Reset rejection status back to PENDING if it was REJECTED
        if (exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            exhibition.setStatus(ExhibitionStatus.PENDING);
            exhibition.setRejectedReason(null);
            exhibition.setReviewedBy(null);
            exhibition.setReviewedAt(null);
        }

        exhibition = exhibitionRepository.save(exhibition);

        // Upload keyVisual if provided (validations already passed)
        if (keyVisual != null && !keyVisual.isEmpty()) {
            validateImageFile(keyVisual, false);
            uploadOrReplaceAsset(exhibition, keyVisual, ExhibitionAssetType.KEY_VISUAL, "image");
        }

        // Delete old packages and save new packages
        List<ExhibitionPackage> oldPackages = exhibitionPackageRepository.findByExhibition(exhibition);
        exhibitionPackageRepository.deleteAll(oldPackages);

        List<ExhibitionPackage> savedPackages = new ArrayList<>();
        if (request.getPackages() != null && !request.getPackages().isEmpty()) {
            for (ConfigureExhibitionPackageRequest pkgReq : request.getPackages()) {
                PackageTemplate template = packageTemplateService.getPackageTemplateEntity(pkgReq.getTemplateId());

                ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                        .exhibition(exhibition)
                        .template(template)
                        .finalPrice(pkgReq.getFinalPrice())
                        .status(ExhibitionPackageStatus.ACTIVE)
                        .build();

                savedPackages.add(exhibitionPackageRepository.save(exhibitionPackage));
            }
        }

        reviewHistoryService.recordResubmissionOrUpdate(exhibition, organizer, null);

        return exhibitionMapper.toResponse(exhibition, savedPackages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO updateExhibitionMedia(User organizer, UUID uuid, MultipartFile trailerVideo,
            MultipartFile floorPlan, MultipartFile guideline) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Post-approval assets can only be updated if approved or active
        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            log.error("Cannot update media assets for exhibition with status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        // 1. Validate all file formats & sizes first
        if (trailerVideo != null && !trailerVideo.isEmpty()) {
            validateVideoFile(trailerVideo);
        }
        if (floorPlan != null && !floorPlan.isEmpty()) {
            validateImageFile(floorPlan, false);
        }
        if (guideline != null && !guideline.isEmpty()) {
            validateImageFile(guideline, false);
        }

        // 2. Perform uploads after all validations pass
        if (trailerVideo != null && !trailerVideo.isEmpty()) {
            uploadOrReplaceAsset(exhibition, trailerVideo, ExhibitionAssetType.TRAILER_VIDEO, "video");
        }
        if (floorPlan != null && !floorPlan.isEmpty()) {
            uploadOrReplaceAsset(exhibition, floorPlan, ExhibitionAssetType.FLOOR_PLAN, "image");
        }
        if (guideline != null && !guideline.isEmpty()) {
            uploadOrReplaceAsset(exhibition, guideline, ExhibitionAssetType.GUIDELINE, "image");
        }

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO approveExhibition(User admin, UUID uuid) {
        if (admin == null || admin.getId() == null) {
            log.error("Admin authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (admin.getRole() != Role.ADMIN) {
            log.error("User {} is not an ADMIN", admin.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (exhibition.getStatus() != ExhibitionStatus.PENDING) {
            log.error("Exhibition {} is not PENDING approval (status: {})", uuid, exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_ALREADY_REVIEWED);
        }

        if (!timelinePolicy.hasMinimumLeadTime(exhibition.getStartDate())) {
            log.error("Cannot approve exhibition {}: start date {} does not meet minimum lead time requirement",
                    exhibition.getId(), exhibition.getStartDate());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        for (ExhibitionPackage pkg : packages) {
            if (pkg.getTemplate() != null && pkg.getFinalPrice().compareTo(pkg.getTemplate().getPrice()) < 0) {
                log.error("Cannot approve exhibition {}: package {} final price {} is below template floor price {}",
                        exhibition.getId(), pkg.getId(), pkg.getFinalPrice(), pkg.getTemplate().getPrice());
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
        }

        exhibition.setStatus(ExhibitionStatus.REGISTRATION);
        exhibition.setReviewedBy(admin);
        exhibition.setReviewedAt(Instant.now());

        exhibition = exhibitionRepository.save(exhibition);
        reviewHistoryService.recordReviewResult(exhibition, admin, ExhibitionReviewStatus.APPROVED, null);

        packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO rejectExhibition(User admin, UUID uuid, RejectExhibitionRequest request) {
        if (admin == null || admin.getId() == null) {
            log.error("Admin authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (admin.getRole() != Role.ADMIN) {
            log.error("User {} is not an ADMIN", admin.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (exhibition.getStatus() != ExhibitionStatus.PENDING) {
            log.error("Exhibition {} is not PENDING rejection (status: {})", uuid, exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_ALREADY_REVIEWED);
        }

        exhibition.setStatus(ExhibitionStatus.REJECTED);
        exhibition.setRejectedReason(request.getRejectedReason());
        exhibition.setReviewedBy(admin);
        exhibition.setReviewedAt(Instant.now());

        // Increment rejection count
        int newRejectionCount = exhibition.getRejectionCount() + 1;
        exhibition.setRejectionCount(newRejectionCount);

        if (newRejectionCount >= 3) {
            log.info("Exhibition {} reached maximum rejection limit (3). Renaming to release name.",
                    exhibition.getId());
            exhibition
                    .setName(exhibition.getName() + " (Rejected-" + UUID.randomUUID().toString().substring(0, 8) + ")");
        }

        exhibition = exhibitionRepository.save(exhibition);
        reviewHistoryService.recordReviewResult(exhibition, admin, ExhibitionReviewStatus.REJECTED,
                request.getRejectedReason());

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private void validateImageFile(MultipartFile file, boolean required) {
        if (file == null || file.isEmpty()) {
            if (required) {
                log.error("Required image file is missing or empty");
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
            return;
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            log.error("Unsupported image file type: {}", contentType);
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            log.error("Image file size {} exceeds 10MB", file.getSize());
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("video/mp4")) {
            log.error("Unsupported video file type: {}", contentType);
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (file.getSize() > 100 * 1024 * 1024) {
            log.error("Video file size {} exceeds 100MB", file.getSize());
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private void uploadOrReplaceAsset(Exhibition exhibition, MultipartFile file, ExhibitionAssetType type,
            String resourceType) {
        ExhibitionAsset existingAsset = exhibitionAssetRepository.findByExhibitionIdAndType(exhibition.getId(), type)
                .orElse(null);
        CloudinaryResponse uploadRes = cloudService.upload(file);
        deleteCloudAssetOnRollback(uploadRes.getPublicId(), resourceType);
        if (existingAsset != null) {
            String oldPublicId = existingAsset.getPublicId();
            existingAsset.setAssetUrl(uploadRes.getUrl());
            existingAsset.setPublicId(uploadRes.getPublicId());
            exhibitionAssetRepository.save(existingAsset);
            deleteCloudAssetAfterCommit(oldPublicId, resourceType);
        } else {
            ExhibitionAsset newAsset = ExhibitionAsset.builder()
                    .exhibition(exhibition)
                    .assetUrl(uploadRes.getUrl())
                    .publicId(uploadRes.getPublicId())
                    .type(type)
                    .build();
            exhibitionAssetRepository.save(newAsset);
            exhibition.getAssets().add(newAsset);
        }
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO uploadSponsorLogo(User organizer, UUID uuid, String name, MultipartFile file) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            log.error("Cannot upload sponsor logo for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        long currentSponsorCount = exhibition.getAssets() == null ? 0
                : exhibition.getAssets().stream()
                        .filter(a -> a.getType() == ExhibitionAssetType.SPONSOR_LOGO)
                        .count();
        if (currentSponsorCount >= MAX_SPONSORS) {
            log.error("Exhibition {} already reached maximum sponsor limit of {}", uuid, MAX_SPONSORS);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        validateImageFile(file, true);

        CloudinaryResponse uploadRes = cloudService.upload(file);
        deleteCloudAssetOnRollback(uploadRes.getPublicId(), "image");
        ExhibitionAsset sponsorLogoAsset = ExhibitionAsset.builder()
                .exhibition(exhibition)
                .name(name != null ? name.trim() : null)
                .assetUrl(uploadRes.getUrl())
                .publicId(uploadRes.getPublicId())
                .type(ExhibitionAssetType.SPONSOR_LOGO)
                .build();
        exhibitionAssetRepository.save(sponsorLogoAsset);
        exhibition.getAssets().add(sponsorLogoAsset);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO updateSponsorLogo(User organizer, UUID uuid, UUID assetId, String name,
            MultipartFile file) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            log.error("Cannot update sponsor logo for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionAsset asset = exhibitionAssetRepository.findById(assetId)
                .orElseThrow(() -> {
                    log.error("Sponsor logo asset not found for ID: {}", assetId);
                    return new AppException(ErrorCode.VALIDATION_FAILED);
                });

        if (!asset.getExhibition().getId().equals(exhibition.getId())
                || asset.getType() != ExhibitionAssetType.SPONSOR_LOGO) {
            log.error("Asset {} does not belong to exhibition {}", assetId, uuid);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (name != null && !name.isBlank()) {
            asset.setName(name.trim());
        }

        if (file != null && !file.isEmpty()) {
            validateImageFile(file, true);

            String oldPublicId = asset.getPublicId();
            CloudinaryResponse uploadRes = cloudService.upload(file);
            deleteCloudAssetOnRollback(uploadRes.getPublicId(), "image");
            asset.setAssetUrl(uploadRes.getUrl());
            asset.setPublicId(uploadRes.getPublicId());
            deleteCloudAssetAfterCommit(oldPublicId, "image");
        }

        exhibitionAssetRepository.save(asset);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO deleteSponsorLogo(User organizer, UUID uuid, UUID assetId) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            log.error("Cannot delete sponsor logo for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionAsset asset = exhibitionAssetRepository.findById(assetId)
                .orElseThrow(() -> {
                    log.error("Sponsor logo asset not found for ID: {}", assetId);
                    return new AppException(ErrorCode.VALIDATION_FAILED);
                });

        if (!asset.getExhibition().getId().equals(exhibition.getId())
                || asset.getType() != ExhibitionAssetType.SPONSOR_LOGO) {
            log.error("Asset {} does not belong to exhibition {}", assetId, uuid);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        String publicId = asset.getPublicId();
        exhibitionAssetRepository.delete(asset);
        exhibition.getAssets().remove(asset);
        deleteCloudAssetAfterCommit(publicId, "image");

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    private void deleteCloudAssetOnRollback(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteCloudAsset(publicId, resourceType);
                }
            }
        });
    }

    private void deleteCloudAssetAfterCommit(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteCloudAsset(publicId, resourceType);
                }
            });
            return;
        }
        deleteCloudAsset(publicId, resourceType);
    }

    private void deleteCloudAsset(String publicId, String resourceType) {
        try {
            cloudService.delete(publicId, resourceType);
        } catch (RuntimeException exception) {
            log.warn("Failed to clean up Cloudinary asset {}", publicId, exception);
        }
    }

    @Override
    @Transactional
    public ExhibitionPackageResponseDTO addExhibitionPackage(User organizer, UUID uuid,
            ConfigureExhibitionPackageRequest request) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() != ExhibitionStatus.PENDING && exhibition.getStatus() != ExhibitionStatus.REJECTED) {
            log.error("Cannot add package for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        List<ExhibitionPackage> currentPackages = exhibitionPackageRepository.findByExhibition(exhibition);
        if (currentPackages.size() >= 3) {
            log.error("Exhibition packages size exceeds limit of 3");
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        PackageTemplate template = packageTemplateService.getPackageTemplateEntity(request.getTemplateId());

        if (request.getFinalPrice().compareTo(template.getPrice()) < 0) {
            log.error("Package final price {} is below floor price {}", request.getFinalPrice(), template.getPrice());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        boolean duplicatePriority = currentPackages.stream()
                .anyMatch(pkg -> pkg.getTemplate().getListingPriority() == template.getListingPriority());
        if (duplicatePriority) {
            log.error("Duplicate package priority type {} is not allowed", template.getListingPriority());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .exhibition(exhibition)
                .template(template)
                .finalPrice(request.getFinalPrice())
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();

        exhibitionPackage = exhibitionPackageRepository.save(exhibitionPackage);
        return exhibitionMapper.toPackageResponse(exhibitionPackage);
    }

    @Override
    @Transactional
    public ExhibitionPackageResponseDTO updateExhibitionPackage(User organizer, UUID uuid, Integer packageId,
            ConfigureExhibitionPackageRequest request) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() != ExhibitionStatus.PENDING && exhibition.getStatus() != ExhibitionStatus.REJECTED) {
            log.error("Cannot update package for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageId(packageId)) {
            log.error("Cannot update package {} because exhibitors have already registered", packageId);
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionPackage exhibitionPackage = exhibitionPackageRepository.findById(packageId)
                .orElseThrow(() -> {
                    log.error("Exhibition package not found for ID: {}", packageId);
                    return new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND);
                });

        if (!exhibitionPackage.getExhibition().getId().equals(exhibition.getId())) {
            log.error("Package {} does not belong to exhibition {}", packageId, uuid);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        PackageTemplate template = packageTemplateService.getPackageTemplateEntity(request.getTemplateId());

        if (!exhibitionPackage.getTemplate().getId().equals(template.getId())) {
            List<ExhibitionPackage> currentPackages = exhibitionPackageRepository.findByExhibition(exhibition);
            boolean duplicatePriority = currentPackages.stream()
                    .filter(pkg -> !pkg.getId().equals(packageId))
                    .anyMatch(pkg -> pkg.getTemplate().getListingPriority() == template.getListingPriority());
            if (duplicatePriority) {
                log.error("Duplicate package priority type {} is not allowed", template.getListingPriority());
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
            exhibitionPackage.setTemplate(template);
        }

        if (request.getFinalPrice().compareTo(template.getPrice()) < 0) {
            log.error("Package final price {} is below floor price {}", request.getFinalPrice(), template.getPrice());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        exhibitionPackage.setFinalPrice(request.getFinalPrice());
        exhibitionPackage = exhibitionPackageRepository.save(exhibitionPackage);
        return exhibitionMapper.toPackageResponse(exhibitionPackage);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO deleteExhibitionPackage(User organizer, UUID uuid, Integer packageId) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() != ExhibitionStatus.PENDING && exhibition.getStatus() != ExhibitionStatus.REJECTED) {
            log.error("Cannot delete package for exhibition status {}", exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageId(packageId)) {
            log.error("Cannot delete package {} because exhibitors have already registered", packageId);
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionPackage exhibitionPackage = exhibitionPackageRepository.findById(packageId)
                .orElseThrow(() -> {
                    log.error("Exhibition package not found for ID: {}", packageId);
                    return new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND);
                });

        if (!exhibitionPackage.getExhibition().getId().equals(exhibition.getId())) {
            log.error("Package {} does not belong to exhibition {}", packageId, uuid);
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        exhibitionPackageRepository.delete(exhibitionPackage);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitionResponseDTO> searchExhibitionsForVisitor(
            String keyword, ExhibitionStatus status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();

        List<ExhibitionStatus> publicStatuses = List.of(
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);
        if (status != null && !publicStatuses.contains(status)) {
            return PageResponse.from(Page.empty(pageable));
        }
        List<ExhibitionStatus> visitorStatuses = status == null ? publicStatuses : List.of(status);

        Page<ExhibitionResponseDTO> exhibitions = exhibitionRepository.searchExhibitions(
                normalizedKeyword, visitorStatuses, normalizedCategory, startDate, endDate, pageable)
                .map(e -> exhibitionMapper.toPublicResponse(e, null));

        return PageResponse.from(exhibitions);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitionResponseDTO> searchExhibitionsForExhibitor(
            String keyword, String category, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();

        List<ExhibitionStatus> exhibitorStatuses = List.of(
                ExhibitionStatus.REGISTRATION,
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE);

        Page<ExhibitionResponseDTO> exhibitions = exhibitionRepository.searchExhibitions(
                normalizedKeyword, exhibitorStatuses, normalizedCategory, startDate, endDate, pageable)
                .map(exhibitionMapper::toResponse);

        return PageResponse.from(exhibitions);
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionDetailForExhibitor(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (exhibition.getStatus() != ExhibitionStatus.REGISTRATION
                && exhibition.getStatus() != ExhibitionStatus.PUBLISHED
                && exhibition.getStatus() != ExhibitionStatus.ACTIVE) {
            log.error("Exhibition {} is not open for exhibitors (status: {})", uuid, exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
        }

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO publishExhibition(User organizer, UUID uuid) {
        if (organizer == null || organizer.getId() == null) {
            log.error("Organizer authentication failed: null or missing ID");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.error("Exhibition not found for UUID: {}", uuid);
                    return new AppException(ErrorCode.EXHIBITION_NOT_FOUND);
                });

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            log.error("Organizer {} is not authorized for exhibition {}", organizer.getId(), uuid);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() != ExhibitionStatus.REGISTRATION) {
            log.error("Cannot publish exhibition {} with status {}", uuid, exhibition.getStatus());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        exhibition.setStatus(ExhibitionStatus.PUBLISHED);
        exhibition = exhibitionRepository.save(exhibition);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }
}
