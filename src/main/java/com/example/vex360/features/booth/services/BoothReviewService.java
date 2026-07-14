package com.example.vex360.features.booth.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentPlacementDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewHotspotItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewMediaItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewPanoramaItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewProductContentItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewProductItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothReviewContextDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothSummaryDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;
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
    private final BoothReviewSnapshotFactory snapshotFactory;
    private final BoothReviewDiffService diffService;

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
    public BoothReviewRequestSummaryDTO submitReview(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothByIdForUpdate(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        boothReviewPolicyService.assertCanSubmitReview(booth);

        BoothReviewRequest previousRequest = boothReviewRequestRepository
                .findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId())
                .orElse(null);
        int versionNumber = Math.toIntExact(boothReviewRequestRepository.countByBoothId(booth.getId()) + 1);
        BoothReviewSnapshot snapshot = snapshotFactory.create(booth);
        var changeSummary = diffService.buildSummary(snapshot, previousRequest, versionNumber);
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
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        assertPending(request);
        request.setStatus(BoothReviewStatus.APPROVED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectedReason(null);
        request.getBooth().setStatus(BoothStatus.PUBLISHED);
        BoothReviewRequest saved = boothReviewRequestRepository.save(request);
        boothRepository.save(request.getBooth());
        return toSummary(saved);
    }

    @Transactional
    public BoothReviewRequestSummaryDTO reject(
            User organizer,
            UUID exhibitionUuid,
            UUID requestId,
            RejectBoothReviewRequest rejectRequest) {
        BoothReviewRequest request = boothReviewPolicyService
                .getOrganizerReviewRequest(organizer, exhibitionUuid, requestId);
        assertPending(request);
        if (rejectRequest == null || rejectRequest.getRejectedReason() == null
                || rejectRequest.getRejectedReason().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        request.setStatus(BoothReviewStatus.REJECTED);
        request.setReviewedBy(organizer);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectedReason(rejectRequest.getRejectedReason().trim());
        request.getBooth().setStatus(BoothStatus.DRAFT);
        BoothReviewRequest saved = boothReviewRequestRepository.save(request);
        boothRepository.save(request.getBooth());
        return toSummary(saved);
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
        boolean canReview = pendingRequest != null;
        return OrganizerBoothContentOverviewDTO.builder()
                .booth(toOrganizerBoothSummary(booth))
                .reviewContext(OrganizerBoothReviewContextDTO.builder()
                        .pendingReviewRequestId(canReview ? pendingRequest.getId() : null)
                        .pendingReviewVersion(canReview ? pendingRequest.getVersionNumber() : null)
                        .canApprove(canReview)
                        .canReject(canReview)
                        .build())
                .contentOverview(toContentOverview(booth))
                .build();
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

    private BoothReviewContentOverviewDTO toContentOverview(Booth booth) {
        List<BoothReviewPanoramaItemDTO> panoramaItems = new ArrayList<>();
        List<BoothReviewHotspotItemDTO> hotspotItems = new ArrayList<>();
        Map<UUID, BoothReviewProductItemDTO> products = new LinkedHashMap<>();
        Map<UUID, BoothReviewMediaItemDTO> mediaAssets = new LinkedHashMap<>();
        Map<UUID, BoothReviewProductContentItemDTO> productContents = new LinkedHashMap<>();

        for (Panorama panorama : sortedPanoramas(booth)) {
            List<Hotspot> hotspots = safeHotspots(panorama);
            panoramaItems.add(BoothReviewPanoramaItemDTO.builder()
                    .id(panorama.getId()).name(panorama.getName()).imageUrl(panorama.getImageUrl())
                    .orderIndex(panorama.getOrderIndex()).isDefault(panorama.getIsDefault())
                    .hotspotCount(hotspots.size()).build());
            for (Hotspot hotspot : hotspots) {
                hotspotItems.add(toHotspotItem(panorama, hotspot));
                BoothReviewContentPlacementDTO placement = toPlacement(panorama, hotspot);
                if (hotspot.getType() == HotspotType.PRODUCT && hotspot.getProduct() != null) {
                    addProduct(products, productContents, hotspot.getProduct(), placement);
                }
                if (hotspot.getType() == HotspotType.MEDIA && hotspot.getMediaAsset() != null) {
                    addMedia(mediaAssets, hotspot.getMediaAsset(), placement);
                }
            }
        }

        return BoothReviewContentOverviewDTO.builder()
                .panoramaCount(panoramaItems.size())
                .hotspotCount(hotspotItems.size())
                .productCount(products.size())
                .productContentCount(productContents.size())
                .mediaAssetCount(mediaAssets.size())
                .panoramas(panoramaItems)
                .hotspots(hotspotItems)
                .products(new ArrayList<>(products.values()))
                .mediaAssets(new ArrayList<>(mediaAssets.values()))
                .build();
    }

    private BoothReviewHotspotItemDTO toHotspotItem(Panorama panorama, Hotspot hotspot) {
        Panorama target = hotspot.getTargetPanorama();
        Product product = hotspot.getProduct();
        MediaAsset media = hotspot.getMediaAsset();
        return BoothReviewHotspotItemDTO.builder()
                .id(hotspot.getId()).type(hotspot.getType()).name(hotspot.getName())
                .sourcePanoramaId(panorama.getId()).sourcePanoramaName(panorama.getName())
                .targetPanoramaId(target == null ? null : target.getId())
                .targetPanoramaName(target == null ? null : target.getName())
                .productId(product == null ? null : product.getId())
                .productName(product == null ? null : product.getName())
                .mediaAssetId(media == null ? null : media.getId())
                .mediaAssetName(media == null ? null : media.getName())
                .infoText(hotspot.getInfoText())
                .xPosition(hotspot.getXPosition()).yPosition(hotspot.getYPosition()).zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle()).scale(hotspot.getScale()).zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction()).infoContentType(hotspot.getInfoContentType())
                .corners(boothMapper.toHotspotCornersDTO(hotspot))
                .build();
    }

    private void addProduct(
            Map<UUID, BoothReviewProductItemDTO> products,
            Map<UUID, BoothReviewProductContentItemDTO> allContents,
            Product product,
            BoothReviewContentPlacementDTO placement) {
        BoothReviewProductItemDTO item = products.computeIfAbsent(product.getId(), ignored -> {
            List<BoothReviewProductContentItemDTO> contents = safeContents(product).stream()
                    .sorted(Comparator.comparing(ProductContent::getOrderIndex,
                            Comparator.nullsLast(Integer::compareTo)))
                    .map(this::toProductContentItem)
                    .toList();
            contents.forEach(content -> allContents.putIfAbsent(content.getId(), content));
            return BoothReviewProductItemDTO.builder()
                    .id(product.getId()).name(product.getName()).sku(product.getSku())
                    .description(product.getDescription()).thumbnailUrl(product.getThumbnailUrl())
                    .price(product.getPrice()).currency(product.getCurrency()).status(product.getStatus())
                    .usageCount(0).placements(new ArrayList<>()).contents(contents).build();
        });
        item.setUsageCount(item.getUsageCount() + 1);
        item.getPlacements().add(placement);
    }

    private BoothReviewProductContentItemDTO toProductContentItem(ProductContent content) {
        return BoothReviewProductContentItemDTO.builder()
                .id(content.getId()).type(content.getType()).url(content.getContentUrl())
                .mimeType(content.getMimeType()).fileSize(content.getFileSize())
                .orderIndex(content.getOrderIndex()).build();
    }

    private void addMedia(
            Map<UUID, BoothReviewMediaItemDTO> mediaAssets,
            MediaAsset media,
            BoothReviewContentPlacementDTO placement) {
        BoothReviewMediaItemDTO item = mediaAssets.computeIfAbsent(media.getId(),
                ignored -> BoothReviewMediaItemDTO.builder()
                        .id(media.getId()).name(media.getName()).type(media.getType()).url(media.getUrl())
                        .mimeType(media.getMimeType()).fileSize(media.getFileSize())
                        .usageCount(0).placements(new ArrayList<>()).build());
        item.setUsageCount(item.getUsageCount() + 1);
        item.getPlacements().add(placement);
    }

    private BoothReviewContentPlacementDTO toPlacement(Panorama panorama, Hotspot hotspot) {
        return BoothReviewContentPlacementDTO.builder()
                .panoramaId(panorama.getId()).panoramaName(panorama.getName())
                .hotspotId(hotspot.getId()).hotspotName(hotspot.getName()).build();
    }

    private BoothReviewRequestSummaryDTO toSummary(BoothReviewRequest request) {
        Booth booth = request.getBooth();
        Company company = booth.getCompany();
        Exhibition exhibition = getExhibition(booth);
        return BoothReviewRequestSummaryDTO.builder()
                .id(request.getId()).versionNumber(request.getVersionNumber())
                .boothId(booth.getId()).boothName(booth.getName())
                .companyId(company == null ? null : company.getId())
                .companyName(company == null ? null : company.getName())
                .exhibitionUuid(exhibition == null ? null : exhibition.getUuid())
                .exhibitionName(exhibition == null ? null : exhibition.getName())
                .status(request.getStatus()).submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt()).rejectedReason(request.getRejectedReason())
                .changeSummary(diffService.readSummary(request.getChangeSummaryJson()))
                .build();
    }

    private OrganizerBoothSummaryDTO toOrganizerBoothSummary(Booth booth) {
        Company company = booth.getCompany();
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        ExhibitionPackage exhibitionPackage = registration == null ? null : registration.getExhibitionPackage();
        String packageName = registration == null ? null : registration.getPackageNameSnapshot();
        if (packageName == null && exhibitionPackage != null && exhibitionPackage.getTemplate() != null) {
            packageName = exhibitionPackage.getTemplate().getName();
        }
        return OrganizerBoothSummaryDTO.builder()
                .id(booth.getId()).companyId(company == null ? null : company.getId())
                .ownerName(company == null || company.getOwnerUser() == null
                        ? null : company.getOwnerUser().getFullName())
                .packageName(packageName)
                .contactEmail(company == null ? null : company.getEmail())
                .contactPhone(company == null ? null : company.getPhone())
                .name(booth.getName()).description(booth.getDescription())
                .thumbnailUrl(booth.getThumbnailUrl()).backgroundMusicUrl(booth.getBackgroundMusicUrl())
                .backgroundMusicFileName(booth.getBackgroundMusicFileName())
                .backgroundMusicFileSize(booth.getBackgroundMusicFileSize())
                .displayTemplateKey(booth.getDisplayTemplateKey()).status(booth.getStatus())
                .updatedAt(booth.getUpdatedAt()).build();
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

    private List<Panorama> sortedPanoramas(Booth booth) {
        if (booth.getPanoramas() == null)
            return List.of();
        return booth.getPanoramas().stream()
                .sorted(Comparator.comparing(Panorama::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<Hotspot> safeHotspots(Panorama panorama) {
        return panorama.getHotspots() == null ? List.of() : panorama.getHotspots();
    }

    private List<ProductContent> safeContents(Product product) {
        return product.getContents() == null ? List.of() : product.getContents();
    }

    private Exhibition getExhibition(Booth booth) {
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null)
            return null;
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
