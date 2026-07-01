package com.example.vex360.features.exhibition.services.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionSummaryResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;

import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.entities.ExhibitionAsset;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.dtos.CloudinaryResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionServiceImpl implements ExhibitionService {

    private final ExhibitionRepository exhibitionRepository;
    private final ExhibitionPackageRepository exhibitionPackageRepository;
    private final PackageTemplateRepository packageTemplateRepository;
    private final ExhibitionAssetRepository exhibitionAssetRepository;
    private final ExhibitorRegistrationRepository exhibitorRegistrationRepository;
    private final ExhibitionMapper exhibitionMapper;
    private final CloudService cloudService;

    @Override
    @Transactional
    public ExhibitionResponseDTO createExhibition(User organizer, CreateExhibitionRequest request, MultipartFile keyVisual, List<MultipartFile> sponsorLogos) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        validateImageFile(keyVisual, true);

        // Validate sponsor logos if provided
        if (sponsorLogos != null && !sponsorLogos.isEmpty()) {
            for (MultipartFile logo : sponsorLogos) {
                validateImageFile(logo, true);
            }
        }

        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            log.error("Exhibition end date {} cannot be before start date {}", request.getEndDate(), request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        long pendingCount = exhibitionRepository.countByOrganizerIdAndStatus(organizer.getId(), ExhibitionStatus.PENDING);
        if (pendingCount >= 3) {
            log.error("Organizer {} already has {} pending exhibition requests", organizer.getId(), pendingCount);
            throw new AppException(ErrorCode.EXHIBITION_LIMIT_EXCEEDED);
        }

        if (exhibitionRepository.existsByName(request.getName().trim())) {
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
                PackageTemplate template = packageTemplateRepository.findById(pkgReq.getTemplateId())
                        .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

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
                PackageTemplate template = packageTemplateRepository.findById(pkgReq.getTemplateId())
                        .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

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
            for (MultipartFile logo : sponsorLogos) {
                if (logo != null && !logo.isEmpty()) {
                    CloudinaryResponse uploadResLogo = cloudService.upload(logo);
                    ExhibitionAsset sponsorLogoAsset = ExhibitionAsset.builder()
                            .exhibition(exhibition)
                            .assetUrl(uploadResLogo.getUrl())
                            .publicId(uploadResLogo.getPublicId())
                            .type(ExhibitionAssetType.SPONSOR_LOGO)
                            .build();
                    exhibitionAssetRepository.save(sponsorLogoAsset);
                    exhibition.getAssets().add(sponsorLogoAsset);
                }
            }
        }

        return exhibitionMapper.toResponse(exhibition, savedPackages);
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionByUuid(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toPublicResponse(exhibition, packages);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExhibitionResponseDTO> searchExhibitionsForAdmin(
            String keyword, ExhibitionStatus status, String category,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();

        Page<ExhibitionResponseDTO> exhibitions = exhibitionRepository.searchExhibitions(
                normalizedKeyword, status, normalizedCategory, startDate, endDate, pageable)
                .map(exhibitionMapper::toResponse);

        return PageResponse.from(exhibitions);
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionSummaryResponseDTO getExhibitionSummary() {
        long total = exhibitionRepository.count();
        List<Object[]> statusCountsRaw = exhibitionRepository.countExhibitionsByStatus();
        Map<String, Long> statusCounts = new HashMap<>();

        // Initialize statusCounts with expected statuses to ensure they are always
        // present
        statusCounts.put("PENDING", 0L);
        statusCounts.put("APPROVED", 0L);
        statusCounts.put("REJECTED", 0L);
        statusCounts.put("ACTIVE", 0L);

        for (Object[] row : statusCountsRaw) {
            ExhibitionStatus status = (ExhibitionStatus) row[0];
            Long count = (Long) row[1];
            if (status != null) {
                statusCounts.put(status.name(), count);
            }
        }

        return ExhibitionSummaryResponseDTO.builder()
                .totalExhibitions(total)
                .pendingExhibitions(statusCounts.getOrDefault("PENDING", 0L))
                .approvedExhibitions(statusCounts.getOrDefault("APPROVED", 0L))
                .rejectedExhibitions(statusCounts.getOrDefault("REJECTED", 0L))
                .activeExhibitions(statusCounts.getOrDefault("ACTIVE", 0L))
                .statusCounts(statusCounts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExhibitionResponseDTO getExhibitionDetailForAdmin(UUID uuid) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionPackageResponseDTO configureExhibitionPackage(User organizer, UUID uuid,
            ConfigureExhibitionPackageRequest request) {
        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        PackageTemplate template = packageTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        if (request.getFinalPrice().compareTo(template.getPrice()) < 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        if (exhibitionPackageRepository.findByExhibitionIdAndTemplateId(exhibition.getId(), template.getId())
                .isPresent()) {
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
    public ExhibitionResponseDTO getExhibitionDetailForOrganizer(User organizer, UUID uuid) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO updateExhibitionForOrganizer(User organizer, UUID uuid, CreateExhibitionRequest request, MultipartFile keyVisual) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
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
            log.error("Exhibition end date {} cannot be before start date {}", request.getEndDate(), request.getStartDate());
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        // Update time-window validation: must be before start date, and no exhibitors registered yet
        if (!LocalDate.now().isBefore(exhibition.getStartDate())) {
            log.error("Cannot update exhibition on or after its start date {}", exhibition.getStartDate());
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageExhibitionId(exhibition.getId())) {
            log.error("Cannot update exhibition because exhibitors have already registered");
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        String trimmedName = request.getName().trim();
        if (!exhibition.getName().equalsIgnoreCase(trimmedName) && exhibitionRepository.existsByName(trimmedName)) {
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
                PackageTemplate template = packageTemplateRepository.findById(pkgReq.getTemplateId())
                        .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

                if (pkgReq.getFinalPrice().compareTo(template.getPrice()) < 0) {
                    log.error("Package final price {} is below floor price {}", pkgReq.getFinalPrice(), template.getPrice());
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
                PackageTemplate template = packageTemplateRepository.findById(pkgReq.getTemplateId())
                        .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

                ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                        .exhibition(exhibition)
                        .template(template)
                        .finalPrice(pkgReq.getFinalPrice())
                        .status(ExhibitionPackageStatus.ACTIVE)
                        .build();

                savedPackages.add(exhibitionPackageRepository.save(exhibitionPackage));
            }
        }

        return exhibitionMapper.toResponse(exhibition, savedPackages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO updateExhibitionMedia(User organizer, UUID uuid, MultipartFile trailerVideo, MultipartFile floorPlan, MultipartFile guideline) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
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
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (admin.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (exhibition.getStatus() != ExhibitionStatus.PENDING) {
            throw new AppException(ErrorCode.EXHIBITION_ALREADY_REVIEWED);
        }

        exhibition.setStatus(ExhibitionStatus.APPROVED);
        exhibition.setReviewedBy(admin);
        exhibition.setReviewedAt(LocalDateTime.now());

        exhibition = exhibitionRepository.save(exhibition);
        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO rejectExhibition(User admin, UUID uuid, RejectExhibitionRequest request) {
        if (admin == null || admin.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (admin.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (exhibition.getStatus() != ExhibitionStatus.PENDING) {
            throw new AppException(ErrorCode.EXHIBITION_ALREADY_REVIEWED);
        }

        exhibition.setStatus(ExhibitionStatus.REJECTED);
        exhibition.setRejectedReason(request.getRejectedReason());
        exhibition.setReviewedBy(admin);
        exhibition.setReviewedAt(LocalDateTime.now());

        // Increment rejection count
        int newRejectionCount = exhibition.getRejectionCount() + 1;
        exhibition.setRejectionCount(newRejectionCount);

        if (newRejectionCount >= 3) {
            log.info("Exhibition {} reached maximum rejection limit (3). Renaming to release name.", exhibition.getId());
            exhibition.setName(exhibition.getName() + " (Rejected-" + UUID.randomUUID().toString().substring(0, 8) + ")");
        }

        exhibition = exhibitionRepository.save(exhibition);
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

    private void uploadOrReplaceAsset(Exhibition exhibition, MultipartFile file, ExhibitionAssetType type, String resourceType) {
        ExhibitionAsset existingAsset = exhibitionAssetRepository.findByExhibitionIdAndType(exhibition.getId(), type)
                .orElse(null);
        if (existingAsset != null) {
            cloudService.delete(existingAsset.getPublicId(), resourceType);
            CloudinaryResponse uploadRes = cloudService.upload(file);
            existingAsset.setAssetUrl(uploadRes.getUrl());
            existingAsset.setPublicId(uploadRes.getPublicId());
            exhibitionAssetRepository.save(existingAsset);
        } else {
            CloudinaryResponse uploadRes = cloudService.upload(file);
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
    public ExhibitionResponseDTO uploadSponsorLogo(User organizer, UUID uuid, MultipartFile file) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        validateImageFile(file, true);

        CloudinaryResponse uploadRes = cloudService.upload(file);
        ExhibitionAsset sponsorLogoAsset = ExhibitionAsset.builder()
                .exhibition(exhibition)
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
    public ExhibitionResponseDTO updateSponsorLogo(User organizer, UUID uuid, UUID assetId, MultipartFile file) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionAsset asset = exhibitionAssetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED));

        if (!asset.getExhibition().getId().equals(exhibition.getId()) || asset.getType() != ExhibitionAssetType.SPONSOR_LOGO) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        validateImageFile(file, true);

        try {
            cloudService.delete(asset.getPublicId(), "image");
        } catch (Exception e) {
            log.error("Failed to delete old asset from Cloudinary: {}", asset.getPublicId(), e);
        }

        CloudinaryResponse uploadRes = cloudService.upload(file);
        asset.setAssetUrl(uploadRes.getUrl());
        asset.setPublicId(uploadRes.getPublicId());
        exhibitionAssetRepository.save(asset);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionResponseDTO deleteSponsorLogo(User organizer, UUID uuid, UUID assetId) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() == ExhibitionStatus.PENDING || exhibition.getStatus() == ExhibitionStatus.REJECTED) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionAsset asset = exhibitionAssetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED));

        if (!asset.getExhibition().getId().equals(exhibition.getId()) || asset.getType() != ExhibitionAssetType.SPONSOR_LOGO) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        try {
            cloudService.delete(asset.getPublicId(), "image");
        } catch (Exception e) {
            log.error("Failed to delete asset from Cloudinary: {}", asset.getPublicId(), e);
        }

        exhibitionAssetRepository.delete(asset);
        exhibition.getAssets().remove(asset);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }

    @Override
    @Transactional
    public ExhibitionPackageResponseDTO addExhibitionPackage(User organizer, UUID uuid, ConfigureExhibitionPackageRequest request) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibition.getStatus() != ExhibitionStatus.PENDING && exhibition.getStatus() != ExhibitionStatus.REJECTED) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        List<ExhibitionPackage> currentPackages = exhibitionPackageRepository.findByExhibition(exhibition);
        if (currentPackages.size() >= 3) {
            log.error("Exhibition packages size exceeds limit of 3");
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        PackageTemplate template = packageTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

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
    public ExhibitionPackageResponseDTO updateExhibitionPackage(User organizer, UUID uuid, Integer packageId, ConfigureExhibitionPackageRequest request) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageId(packageId)) {
            log.error("Cannot update package {} because exhibitors have already registered", packageId);
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionPackage exhibitionPackage = exhibitionPackageRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND));

        if (!exhibitionPackage.getExhibition().getId().equals(exhibition.getId())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        PackageTemplate template = packageTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

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
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Exhibition exhibition = exhibitionRepository.findByUuid(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        if (!exhibition.getOrganizer().getId().equals(organizer.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (exhibitorRegistrationRepository.existsByExhibitionPackageId(packageId)) {
            log.error("Cannot delete package {} because exhibitors have already registered", packageId);
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }

        ExhibitionPackage exhibitionPackage = exhibitionPackageRepository.findById(packageId)
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND));

        if (!exhibitionPackage.getExhibition().getId().equals(exhibition.getId())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        exhibitionPackageRepository.delete(exhibitionPackage);

        List<ExhibitionPackage> packages = exhibitionPackageRepository.findByExhibition(exhibition);
        return exhibitionMapper.toResponse(exhibition, packages);
    }
}
