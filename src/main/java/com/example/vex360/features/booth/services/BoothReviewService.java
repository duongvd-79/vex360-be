package com.example.vex360.features.booth.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotPanoramaSummaryDTO;
import com.example.vex360.features.booth.dtos.response.HotspotProductSummaryDTO;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewChangeItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewChangeSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentPlacementDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewMediaItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewPanoramaItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewProductItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestDetailDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothReviewChangeScope;
import com.example.vex360.features.booth.enums.BoothReviewChangeType;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothReviewService {
    private final BoothRepository boothRepository;
    private final BoothReviewRequestRepository boothReviewRequestRepository;
    private final PanoramaRepository panoramaRepository;
    private final CompanyService companyService;
    private final BoothMapper boothMapper;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final ObjectMapper objectMapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .findAndAddModules()
            .build();


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

        BoothReviewSnapshot snapshot = toSnapshot(booth);
        BoothReviewChangeSummaryDTO changeSummary = buildChangeSummary(booth, snapshot);
        BoothReviewRequest reviewRequest = BoothReviewRequest.builder()
                .booth(booth)
                .status(BoothReviewStatus.PENDING)
                .submittedBy(currentUser)
                .contentSnapshotJson(writeJson(snapshot))
                .changeSummaryJson(writeJson(changeSummary))
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
        BoothReviewChangeSummaryDTO changeSummary = readChangeSummary(request.getChangeSummaryJson());
        return BoothReviewRequestDetailDTO.builder()
                .request(toSummary(request))
                .booth(toBoothResponseDTO(request, booth))
                .contentOverview(toContentOverview(request, booth))
                .changeSummary(changeSummary)
                .build();
    }

    private BoothResponseDTO toBoothResponseDTO(BoothReviewRequest request, Booth booth) {
        BoothReviewSnapshot snapshot = readSnapshot(request.getContentSnapshotJson());
        if (snapshot == null) {
            return boothMapper.toBoothResponseDTO(booth);
        }
        BoothSnapshot bs = snapshot.getBooth();

        List<PanoramaResponseDTO> panoramaDTOs = new ArrayList<>();
        List<PanoramaSnapshot> sortedPanoramas = safeList(snapshot.getPanoramas()).stream()
                .sorted(Comparator.comparing(
                        PanoramaSnapshot::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

        for (PanoramaSnapshot ps : sortedPanoramas) {
            List<HotspotResponseDTO> hotspotDTOs = new ArrayList<>();
            List<HotspotSnapshot> matchingHotspots = safeList(snapshot.getHotspots()).stream()
                    .filter(h -> Objects.equals(h.getSourcePanoramaId(), ps.getId()))
                    .sorted(Comparator.comparing(
                            HotspotSnapshot::getName,
                            Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();

            for (HotspotSnapshot hs : matchingHotspots) {
                String targetPanoramaName = null;
                HotspotPanoramaSummaryDTO targetPanoramaSummary = null;
                if (hs.getTargetPanoramaId() != null) {
                    targetPanoramaName = safeList(snapshot.getPanoramas()).stream()
                            .filter(p -> Objects.equals(p.getId(), hs.getTargetPanoramaId()))
                            .map(PanoramaSnapshot::getName)
                            .findFirst()
                            .orElse(null);
                    targetPanoramaSummary = new HotspotPanoramaSummaryDTO(hs.getTargetPanoramaId(), targetPanoramaName);
                }

                HotspotProductSummaryDTO productSummary = hs.getProductId() == null ? null
                        : new HotspotProductSummaryDTO(
                                hs.getProductId(),
                                hs.getProductName(),
                                hs.getProductSku(),
                                safeList(snapshot.getProductPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getProductId()))
                                        .map(PlacementSnapshot::getThumbnailUrl)
                                        .findFirst()
                                        .orElse(null),
                                safeList(snapshot.getProductPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getProductId()))
                                        .map(PlacementSnapshot::getPrice)
                                        .findFirst()
                                        .orElse(null),
                                safeList(snapshot.getProductPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getProductId()))
                                        .map(PlacementSnapshot::getCurrency)
                                        .findFirst()
                                        .orElse(null),
                                safeList(snapshot.getProductPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getProductId()))
                                        .map(PlacementSnapshot::getProductStatus)
                                        .findFirst()
                                        .orElse(null)
                        );

                MediaAssetResponseDTO mediaAssetDTO = hs.getMediaAssetId() == null ? null
                        : new MediaAssetResponseDTO(
                                hs.getMediaAssetId(),
                                booth.getCompany() == null ? null : booth.getCompany().getId(),
                                hs.getMediaAssetName(),
                                hs.getMediaAssetType(),
                                hs.getMediaAssetUrl(),
                                null,
                                safeList(snapshot.getMediaPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getMediaAssetId()))
                                        .map(PlacementSnapshot::getMimeType)
                                        .findFirst()
                                        .orElse(null),
                                safeList(snapshot.getMediaPlacements()).stream()
                                        .filter(p -> Objects.equals(p.getItemId(), hs.getMediaAssetId()))
                                        .map(PlacementSnapshot::getFileSize)
                                        .findFirst()
                                        .orElse(null),
                                null
                        );

                HotspotCornersDTO cornersDTO = null;
                if (hs.getCornerTlX() != null && hs.getCornerTlY() != null && hs.getCornerTlZ() != null
                        && hs.getCornerTrX() != null && hs.getCornerTrY() != null && hs.getCornerTrZ() != null
                        && hs.getCornerBlX() != null && hs.getCornerBlY() != null && hs.getCornerBlZ() != null
                        && hs.getCornerBrX() != null && hs.getCornerBrY() != null && hs.getCornerBrZ() != null) {
                    cornersDTO = new HotspotCornersDTO(
                            List.of(hs.getCornerTlX(), hs.getCornerTlY(), hs.getCornerTlZ()),
                            List.of(hs.getCornerTrX(), hs.getCornerTrY(), hs.getCornerTrZ()),
                            List.of(hs.getCornerBlX(), hs.getCornerBlY(), hs.getCornerBlZ()),
                            List.of(hs.getCornerBrX(), hs.getCornerBrY(), hs.getCornerBrZ())
                    );
                }

                HotspotResponseDTO hDTO = new HotspotResponseDTO(
                        hs.getId(),
                        hs.getType(),
                        hs.getName(),
                        ps.getId(),
                        hs.getTargetPanoramaId(),
                        targetPanoramaName,
                        targetPanoramaSummary,
                        productSummary,
                        mediaAssetDTO,
                        hs.getInfoText(),
                        hs.getXPosition(),
                        hs.getYPosition(),
                        hs.getZPosition(),
                        hs.getIconStyle(),
                        hs.getScale(),
                        hs.getZIndex(),
                        hs.getMediaClickAction(),
                        hs.getInfoContentType(),
                        cornersDTO
                );
                hotspotDTOs.add(hDTO);
            }

            panoramaDTOs.add(new PanoramaResponseDTO(
                    ps.getId(),
                    ps.getName(),
                    ps.getImageUrl(),
                    ps.getImageKey(),
                    ps.getOrderIndex(),
                    ps.getIsDefault(),
                    hotspotDTOs
            ));
        }

        com.example.vex360.features.company.entities.Company company = booth.getCompany();
        com.example.vex360.features.exhibition.entities.ExhibitorRegistration registration = booth.getExhibitorRegistration();

        UUID exhibitionUuid = null;
        String exhibitionName = null;
        if (registration != null && registration.getExhibitionPackage() != null && registration.getExhibitionPackage().getExhibition() != null) {
            exhibitionUuid = registration.getExhibitionPackage().getExhibition().getUuid();
            exhibitionName = registration.getExhibitionPackage().getExhibition().getName();
        }

        return new BoothResponseDTO(
                booth.getId(),
                company == null ? null : company.getId(),
                registration == null ? null : registration.getUuid(),
                exhibitionUuid,
                exhibitionName,
                bs.getName(),
                bs.getDescription(),
                bs.getThumbnailUrl(),
                bs.getBackgroundMusicUrl(),
                null,
                null,
                bs.getDisplayTemplateKey(),
                request.getBooth().getStatus(),
                booth.getCreatedAt(),
                booth.getUpdatedAt(),
                panoramaDTOs
        );
    }

    private BoothReviewContentOverviewDTO toContentOverview(BoothReviewRequest request, Booth booth) {
        BoothReviewSnapshot snapshot = readSnapshot(request.getContentSnapshotJson());
        return snapshot == null ? toContentOverview(booth) : toContentOverview(snapshot);
    }

    private BoothReviewContentOverviewDTO toContentOverview(Booth booth) {
        List<Panorama> panoramas = sortedPanoramas(booth);
        List<BoothReviewPanoramaItemDTO> panoramaItems = new ArrayList<>();
        Map<UUID, BoothReviewProductItemDTO> productsById = new LinkedHashMap<>();
        Map<UUID, BoothReviewMediaItemDTO> mediaAssetsById = new LinkedHashMap<>();
        int hotspotCount = 0;

        for (Panorama panorama : panoramas) {
            List<Hotspot> hotspots = safeHotspots(panorama);
            hotspotCount += hotspots.size();
            panoramaItems.add(toPanoramaItem(panorama, hotspots.size()));

            for (Hotspot hotspot : hotspots) {
                if (hotspot.getType() == HotspotType.PRODUCT && hotspot.getProduct() != null) {
                    addProductUsage(productsById, hotspot.getProduct(), placement(panorama, hotspot));
                }
                if (hotspot.getType() == HotspotType.MEDIA && hotspot.getMediaAsset() != null) {
                    addMediaUsage(mediaAssetsById, hotspot.getMediaAsset(), placement(panorama, hotspot));
                }
            }
        }

        return BoothReviewContentOverviewDTO.builder()
                .panoramaCount(panoramaItems.size())
                .hotspotCount(hotspotCount)
                .productCount(productsById.size())
                .mediaAssetCount(mediaAssetsById.size())
                .panoramas(panoramaItems)
                .products(new ArrayList<>(productsById.values()))
                .mediaAssets(new ArrayList<>(mediaAssetsById.values()))
                .build();
    }

    private List<Panorama> sortedPanoramas(Booth booth) {
        if (booth.getPanoramas() == null) {
            return List.of();
        }
        return booth.getPanoramas().stream()
                .sorted(Comparator.comparing(
                        Panorama::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<Hotspot> safeHotspots(Panorama panorama) {
        return panorama.getHotspots() == null ? List.of() : panorama.getHotspots();
    }

    private BoothReviewPanoramaItemDTO toPanoramaItem(Panorama panorama, int hotspotCount) {
        return BoothReviewPanoramaItemDTO.builder()
                .id(panorama.getId())
                .name(panorama.getName())
                .imageUrl(panorama.getImageUrl())
                .orderIndex(panorama.getOrderIndex())
                .isDefault(panorama.getIsDefault())
                .hotspotCount(hotspotCount)
                .build();
    }

    private void addProductUsage(
            Map<UUID, BoothReviewProductItemDTO> productsById,
            Product product,
            BoothReviewContentPlacementDTO placement) {
        BoothReviewProductItemDTO item = productsById.computeIfAbsent(product.getId(), key -> BoothReviewProductItemDTO
                .builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .status(product.getStatus())
                .usageCount(0)
                .placements(new ArrayList<>())
                .build());
        item.setUsageCount(item.getUsageCount() + 1);
        item.getPlacements().add(placement);
    }

    private void addMediaUsage(
            Map<UUID, BoothReviewMediaItemDTO> mediaAssetsById,
            MediaAsset mediaAsset,
            BoothReviewContentPlacementDTO placement) {
        BoothReviewMediaItemDTO item = mediaAssetsById.computeIfAbsent(mediaAsset.getId(), key -> BoothReviewMediaItemDTO
                .builder()
                .id(mediaAsset.getId())
                .name(mediaAsset.getName())
                .type(mediaAsset.getType())
                .url(mediaAsset.getUrl())
                .mimeType(mediaAsset.getMimeType())
                .fileSize(mediaAsset.getFileSize())
                .usageCount(0)
                .placements(new ArrayList<>())
                .build());
        item.setUsageCount(item.getUsageCount() + 1);
        item.getPlacements().add(placement);
    }

    private BoothReviewContentPlacementDTO placement(Panorama panorama, Hotspot hotspot) {
        return BoothReviewContentPlacementDTO.builder()
                .panoramaId(panorama.getId())
                .panoramaName(panorama.getName())
                .hotspotId(hotspot.getId())
                .hotspotName(hotspot.getName())
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
                .changeSummary(readChangeSummary(request.getChangeSummaryJson()))
                .build();
    }

    private BoothReviewChangeSummaryDTO buildChangeSummary(Booth booth, BoothReviewSnapshot currentSnapshot) {
        BoothReviewSnapshot previousSnapshot = boothReviewRequestRepository
                .findTopByBoothIdOrderBySubmittedAtDesc(booth.getId())
                .map(BoothReviewRequest::getContentSnapshotJson)
                .map(this::readSnapshot)
                .orElse(null);
        if (previousSnapshot == null) {
            return BoothReviewChangeSummaryDTO.builder()
                    .initialSubmission(true)
                    .addedCount(0)
                    .modifiedCount(0)
                    .removedCount(0)
                    .totalCount(0)
                    .items(List.of())
                    .build();
        }

        List<BoothReviewChangeItemDTO> items = new ArrayList<>();
        compareBooth(previousSnapshot, currentSnapshot, items);
        comparePanoramas(previousSnapshot, currentSnapshot, items);
        compareHotspots(previousSnapshot, currentSnapshot, items);
        comparePlacements(
                BoothReviewChangeScope.PRODUCT_PLACEMENT,
                previousSnapshot.getProductPlacements(),
                currentSnapshot.getProductPlacements(),
                items);
        comparePlacements(
                BoothReviewChangeScope.MEDIA_PLACEMENT,
                previousSnapshot.getMediaPlacements(),
                currentSnapshot.getMediaPlacements(),
                items);

        int addedCount = countByType(items, BoothReviewChangeType.ADDED);
        int modifiedCount = countByType(items, BoothReviewChangeType.MODIFIED);
        int removedCount = countByType(items, BoothReviewChangeType.REMOVED);
        return BoothReviewChangeSummaryDTO.builder()
                .initialSubmission(false)
                .addedCount(addedCount)
                .modifiedCount(modifiedCount)
                .removedCount(removedCount)
                .totalCount(items.size())
                .items(items)
                .build();
    }

    private int countByType(List<BoothReviewChangeItemDTO> items, BoothReviewChangeType type) {
        return (int) items.stream()
                .filter(item -> item.getType() == type)
                .count();
    }

    private void compareBooth(
            BoothReviewSnapshot previous,
            BoothReviewSnapshot current,
            List<BoothReviewChangeItemDTO> items) {
        BoothSnapshot previousBooth = previous.getBooth();
        BoothSnapshot currentBooth = current.getBooth();
        List<String> fields = changedFields(
                fieldChange("name", previousBooth.getName(), currentBooth.getName()),
                fieldChange("description", previousBooth.getDescription(), currentBooth.getDescription()),
                fieldChange("thumbnailUrl", previousBooth.getThumbnailUrl(), currentBooth.getThumbnailUrl()),
                fieldChange("backgroundMusicUrl", previousBooth.getBackgroundMusicUrl(),
                        currentBooth.getBackgroundMusicUrl()),
                fieldChange("displayTemplateKey", previousBooth.getDisplayTemplateKey(),
                        currentBooth.getDisplayTemplateKey()));
        if (!fields.isEmpty()) {
            items.add(changeItem(
                    BoothReviewChangeType.MODIFIED,
                    BoothReviewChangeScope.BOOTH,
                    null,
                    currentBooth.getName(),
                    null,
                    null,
                    fields));
        }
    }

    private void comparePanoramas(
            BoothReviewSnapshot previous,
            BoothReviewSnapshot current,
            List<BoothReviewChangeItemDTO> items) {
        Map<UUID, PanoramaSnapshot> previousById = indexById(previous.getPanoramas(), PanoramaSnapshot::getId);
        Map<UUID, PanoramaSnapshot> currentById = indexById(current.getPanoramas(), PanoramaSnapshot::getId);

        for (PanoramaSnapshot currentItem : currentById.values()) {
            PanoramaSnapshot previousItem = previousById.get(currentItem.getId());
            if (previousItem == null) {
                items.add(changeItem(
                        BoothReviewChangeType.ADDED,
                        BoothReviewChangeScope.PANORAMA,
                        currentItem.getId(),
                        currentItem.getName(),
                        currentItem.getId(),
                        currentItem.getName(),
                        List.of()));
                continue;
            }
            List<String> fields = changedFields(
                    fieldChange("name", previousItem.getName(), currentItem.getName()),
                    fieldChange("imageUrl", previousItem.getImageUrl(), currentItem.getImageUrl()),
                    fieldChange("imageKey", previousItem.getImageKey(), currentItem.getImageKey()),
                    fieldChange("orderIndex", previousItem.getOrderIndex(), currentItem.getOrderIndex()),
                    fieldChange("isDefault", previousItem.getIsDefault(), currentItem.getIsDefault()));
            if (!fields.isEmpty()) {
                items.add(changeItem(
                        BoothReviewChangeType.MODIFIED,
                        BoothReviewChangeScope.PANORAMA,
                        currentItem.getId(),
                        currentItem.getName(),
                        currentItem.getId(),
                        currentItem.getName(),
                        fields));
            }
        }

        for (PanoramaSnapshot previousItem : previousById.values()) {
            if (!currentById.containsKey(previousItem.getId())) {
                items.add(changeItem(
                        BoothReviewChangeType.REMOVED,
                        BoothReviewChangeScope.PANORAMA,
                        previousItem.getId(),
                        previousItem.getName(),
                        previousItem.getId(),
                        previousItem.getName(),
                        List.of()));
            }
        }
    }

    private void compareHotspots(
            BoothReviewSnapshot previous,
            BoothReviewSnapshot current,
            List<BoothReviewChangeItemDTO> items) {
        Map<UUID, HotspotSnapshot> previousById = indexById(previous.getHotspots(), HotspotSnapshot::getId);
        Map<UUID, HotspotSnapshot> currentById = indexById(current.getHotspots(), HotspotSnapshot::getId);

        for (HotspotSnapshot currentItem : currentById.values()) {
            HotspotSnapshot previousItem = previousById.get(currentItem.getId());
            if (previousItem == null) {
                items.add(changeItem(
                        BoothReviewChangeType.ADDED,
                        BoothReviewChangeScope.HOTSPOT,
                        currentItem.getId(),
                        currentItem.getName(),
                        currentItem.getSourcePanoramaId(),
                        currentItem.getSourcePanoramaName(),
                        List.of()));
                continue;
            }
            List<String> fields = changedFields(
                    fieldChange("name", previousItem.getName(), currentItem.getName()),
                    fieldChange("type", previousItem.getType(), currentItem.getType()),
                    fieldChange("sourcePanoramaId", previousItem.getSourcePanoramaId(),
                            currentItem.getSourcePanoramaId()),
                    fieldChange("targetPanoramaId", previousItem.getTargetPanoramaId(),
                            currentItem.getTargetPanoramaId()),
                    fieldChange("productId", previousItem.getProductId(), currentItem.getProductId()),
                    fieldChange("mediaAssetId", previousItem.getMediaAssetId(), currentItem.getMediaAssetId()),
                    fieldChange("infoText", previousItem.getInfoText(), currentItem.getInfoText()),
                    fieldChange("xPosition", previousItem.getXPosition(), currentItem.getXPosition()),
                    fieldChange("yPosition", previousItem.getYPosition(), currentItem.getYPosition()),
                    fieldChange("zPosition", previousItem.getZPosition(), currentItem.getZPosition()),
                    fieldChange("iconStyle", previousItem.getIconStyle(), currentItem.getIconStyle()),
                    fieldChange("scale", previousItem.getScale(), currentItem.getScale()),
                    fieldChange("zIndex", previousItem.getZIndex(), currentItem.getZIndex()),
                    fieldChange("mediaClickAction", previousItem.getMediaClickAction(),
                            currentItem.getMediaClickAction()),
                    fieldChange("infoContentType", previousItem.getInfoContentType(),
                            currentItem.getInfoContentType()),
                    fieldChange("cornerTlX", previousItem.getCornerTlX(), currentItem.getCornerTlX()),
                    fieldChange("cornerTlY", previousItem.getCornerTlY(), currentItem.getCornerTlY()),
                    fieldChange("cornerTlZ", previousItem.getCornerTlZ(), currentItem.getCornerTlZ()),
                    fieldChange("cornerTrX", previousItem.getCornerTrX(), currentItem.getCornerTrX()),
                    fieldChange("cornerTrY", previousItem.getCornerTrY(), currentItem.getCornerTrY()),
                    fieldChange("cornerTrZ", previousItem.getCornerTrZ(), currentItem.getCornerTrZ()),
                    fieldChange("cornerBlX", previousItem.getCornerBlX(), currentItem.getCornerBlX()),
                    fieldChange("cornerBlY", previousItem.getCornerBlY(), currentItem.getCornerBlY()),
                    fieldChange("cornerBlZ", previousItem.getCornerBlZ(), currentItem.getCornerBlZ()),
                    fieldChange("cornerBrX", previousItem.getCornerBrX(), currentItem.getCornerBrX()),
                    fieldChange("cornerBrY", previousItem.getCornerBrY(), currentItem.getCornerBrY()),
                    fieldChange("cornerBrZ", previousItem.getCornerBrZ(), currentItem.getCornerBrZ()));
            if (!fields.isEmpty()) {
                items.add(changeItem(
                        BoothReviewChangeType.MODIFIED,
                        BoothReviewChangeScope.HOTSPOT,
                        currentItem.getId(),
                        currentItem.getName(),
                        currentItem.getSourcePanoramaId(),
                        currentItem.getSourcePanoramaName(),
                        fields));
            }
        }

        for (HotspotSnapshot previousItem : previousById.values()) {
            if (!currentById.containsKey(previousItem.getId())) {
                items.add(changeItem(
                        BoothReviewChangeType.REMOVED,
                        BoothReviewChangeScope.HOTSPOT,
                        previousItem.getId(),
                        previousItem.getName(),
                        previousItem.getSourcePanoramaId(),
                        previousItem.getSourcePanoramaName(),
                        List.of()));
            }
        }
    }

    private void comparePlacements(
            BoothReviewChangeScope scope,
            List<PlacementSnapshot> previousPlacements,
            List<PlacementSnapshot> currentPlacements,
            List<BoothReviewChangeItemDTO> items) {
        Map<String, PlacementSnapshot> previousByKey = indexPlacements(previousPlacements);
        Map<String, PlacementSnapshot> currentByKey = indexPlacements(currentPlacements);

        for (PlacementSnapshot currentItem : currentByKey.values()) {
            PlacementSnapshot previousItem = previousByKey.get(placementKey(currentItem));
            if (previousItem == null) {
                items.add(changeItem(
                        BoothReviewChangeType.ADDED,
                        scope,
                        currentItem.getItemId(),
                        currentItem.getItemName(),
                        currentItem.getPanoramaId(),
                        currentItem.getPanoramaName(),
                        List.of()));
                continue;
            }
            List<String> fields = changedFields(
                    fieldChange("itemName", previousItem.getItemName(), currentItem.getItemName()),
                    fieldChange("sku", previousItem.getSku(), currentItem.getSku()),
                    fieldChange("description", previousItem.getDescription(), currentItem.getDescription()),
                    fieldChange("thumbnailUrl", previousItem.getThumbnailUrl(), currentItem.getThumbnailUrl()),
                    fieldChange("price", previousItem.getPrice(), currentItem.getPrice()),
                    fieldChange("currency", previousItem.getCurrency(), currentItem.getCurrency()),
                    fieldChange("productStatus", previousItem.getProductStatus(), currentItem.getProductStatus()),
                    fieldChange("mediaAssetType", previousItem.getMediaAssetType(), currentItem.getMediaAssetType()),
                    fieldChange("url", previousItem.getUrl(), currentItem.getUrl()),
                    fieldChange("mimeType", previousItem.getMimeType(), currentItem.getMimeType()),
                    fieldChange("fileSize", previousItem.getFileSize(), currentItem.getFileSize()),
                    fieldChange("hotspotName", previousItem.getHotspotName(), currentItem.getHotspotName()),
                    fieldChange("panoramaName", previousItem.getPanoramaName(), currentItem.getPanoramaName()));
            if (!fields.isEmpty()) {
                items.add(changeItem(
                        BoothReviewChangeType.MODIFIED,
                        scope,
                        currentItem.getItemId(),
                        currentItem.getItemName(),
                        currentItem.getPanoramaId(),
                        currentItem.getPanoramaName(),
                        fields));
            }
        }

        for (PlacementSnapshot previousItem : previousByKey.values()) {
            if (!currentByKey.containsKey(placementKey(previousItem))) {
                items.add(changeItem(
                        BoothReviewChangeType.REMOVED,
                        scope,
                        previousItem.getItemId(),
                        previousItem.getItemName(),
                        previousItem.getPanoramaId(),
                        previousItem.getPanoramaName(),
                        List.of()));
            }
        }
    }

    private BoothReviewChangeItemDTO changeItem(
            BoothReviewChangeType type,
            BoothReviewChangeScope scope,
            UUID itemId,
            String itemName,
            UUID panoramaId,
            String panoramaName,
            List<String> fields) {
        return BoothReviewChangeItemDTO.builder()
                .type(type)
                .scope(scope)
                .itemId(itemId)
                .itemName(itemName)
                .panoramaId(panoramaId)
                .panoramaName(panoramaName)
                .fields(fields)
                .build();
    }

    private <T> Map<UUID, T> indexById(List<T> items, java.util.function.Function<T, UUID> idProvider) {
        Map<UUID, T> byId = new LinkedHashMap<>();
        for (T item : safeList(items)) {
            UUID id = idProvider.apply(item);
            if (id != null) {
                byId.put(id, item);
            }
        }
        return byId;
    }

    private Map<String, PlacementSnapshot> indexPlacements(List<PlacementSnapshot> placements) {
        Map<String, PlacementSnapshot> byKey = new LinkedHashMap<>();
        for (PlacementSnapshot placement : safeList(placements)) {
            byKey.put(placementKey(placement), placement);
        }
        return byKey;
    }

    private String placementKey(PlacementSnapshot placement) {
        return placement.getItemId() + ":" + placement.getPanoramaId() + ":" + placement.getHotspotId();
    }

    private String fieldChange(String field, Object previousValue, Object currentValue) {
        return Objects.equals(previousValue, currentValue) ? null : field;
    }

    private List<String> changedFields(String... fields) {
        List<String> changed = new ArrayList<>();
        for (String field : fields) {
            if (field != null) {
                changed.add(field);
            }
        }
        return changed;
    }

    private BoothReviewSnapshot toSnapshot(Booth booth) {
        List<Panorama> panoramas = sortedPanoramas(booth);
        List<PanoramaSnapshot> panoramaSnapshots = new ArrayList<>();
        List<HotspotSnapshot> hotspotSnapshots = new ArrayList<>();
        List<PlacementSnapshot> productPlacements = new ArrayList<>();
        List<PlacementSnapshot> mediaPlacements = new ArrayList<>();

        for (Panorama panorama : panoramas) {
            panoramaSnapshots.add(PanoramaSnapshot.builder()
                    .id(panorama.getId())
                    .name(panorama.getName())
                    .imageUrl(panorama.getImageUrl())
                    .imageKey(panorama.getImageKey())
                    .orderIndex(panorama.getOrderIndex())
                    .isDefault(panorama.getIsDefault())
                    .build());

            for (Hotspot hotspot : safeHotspots(panorama)) {
                hotspotSnapshots.add(toHotspotSnapshot(panorama, hotspot));
                if (hotspot.getType() == HotspotType.PRODUCT && hotspot.getProduct() != null) {
                    productPlacements.add(toProductPlacement(panorama, hotspot, hotspot.getProduct()));
                }
                if (hotspot.getType() == HotspotType.MEDIA && hotspot.getMediaAsset() != null) {
                    mediaPlacements.add(toMediaPlacement(panorama, hotspot, hotspot.getMediaAsset()));
                }
            }
        }

        return BoothReviewSnapshot.builder()
                .booth(BoothSnapshot.builder()
                        .name(booth.getName())
                        .description(booth.getDescription())
                        .thumbnailUrl(booth.getThumbnailUrl())
                        .backgroundMusicUrl(booth.getBackgroundMusicUrl())
                        .displayTemplateKey(booth.getDisplayTemplateKey())
                        .build())
                .panoramas(panoramaSnapshots)
                .hotspots(hotspotSnapshots)
                .productPlacements(productPlacements)
                .mediaPlacements(mediaPlacements)
                .build();
    }

    private HotspotSnapshot toHotspotSnapshot(Panorama panorama, Hotspot hotspot) {
        Product product = hotspot.getProduct();
        MediaAsset mediaAsset = hotspot.getMediaAsset();
        return HotspotSnapshot.builder()
                .id(hotspot.getId())
                .name(hotspot.getName())
                .type(hotspot.getType())
                .sourcePanoramaId(panorama.getId())
                .sourcePanoramaName(panorama.getName())
                .targetPanoramaId(hotspot.getTargetPanorama() == null ? null : hotspot.getTargetPanorama().getId())
                .productId(product == null ? null : product.getId())
                .productName(product == null ? null : product.getName())
                .productSku(product == null ? null : product.getSku())
                .mediaAssetId(mediaAsset == null ? null : mediaAsset.getId())
                .mediaAssetName(mediaAsset == null ? null : mediaAsset.getName())
                .mediaAssetType(mediaAsset == null ? null : mediaAsset.getType())
                .mediaAssetUrl(mediaAsset == null ? null : mediaAsset.getUrl())
                .infoText(hotspot.getInfoText())
                .xPosition(hotspot.getXPosition())
                .yPosition(hotspot.getYPosition())
                .zPosition(hotspot.getZPosition())
                .iconStyle(hotspot.getIconStyle())
                .scale(hotspot.getScale())
                .zIndex(hotspot.getZIndex())
                .mediaClickAction(hotspot.getMediaClickAction())
                .infoContentType(hotspot.getInfoContentType())
                .cornerTlX(hotspot.getCornerTlX())
                .cornerTlY(hotspot.getCornerTlY())
                .cornerTlZ(hotspot.getCornerTlZ())
                .cornerTrX(hotspot.getCornerTrX())
                .cornerTrY(hotspot.getCornerTrY())
                .cornerTrZ(hotspot.getCornerTrZ())
                .cornerBlX(hotspot.getCornerBlX())
                .cornerBlY(hotspot.getCornerBlY())
                .cornerBlZ(hotspot.getCornerBlZ())
                .cornerBrX(hotspot.getCornerBrX())
                .cornerBrY(hotspot.getCornerBrY())
                .cornerBrZ(hotspot.getCornerBrZ())
                .build();
    }

    private PlacementSnapshot toProductPlacement(Panorama panorama, Hotspot hotspot, Product product) {
        return PlacementSnapshot.builder()
                .itemId(product.getId())
                .itemName(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .productStatus(product.getStatus())
                .hotspotId(hotspot.getId())
                .hotspotName(hotspot.getName())
                .panoramaId(panorama.getId())
                .panoramaName(panorama.getName())
                .build();
    }

    private PlacementSnapshot toMediaPlacement(Panorama panorama, Hotspot hotspot, MediaAsset mediaAsset) {
        return PlacementSnapshot.builder()
                .itemId(mediaAsset.getId())
                .itemName(mediaAsset.getName())
                .mediaAssetType(mediaAsset.getType())
                .url(mediaAsset.getUrl())
                .mimeType(mediaAsset.getMimeType())
                .fileSize(mediaAsset.getFileSize())
                .hotspotId(hotspot.getId())
                .hotspotName(hotspot.getName())
                .panoramaId(panorama.getId())
                .panoramaName(panorama.getName())
                .build();
    }

    private BoothReviewContentOverviewDTO toContentOverview(BoothReviewSnapshot snapshot) {
        Map<UUID, Integer> hotspotCountByPanoramaId = new LinkedHashMap<>();
        for (HotspotSnapshot hotspot : safeList(snapshot.getHotspots())) {
            hotspotCountByPanoramaId.merge(hotspot.getSourcePanoramaId(), 1, Integer::sum);
        }

        List<BoothReviewPanoramaItemDTO> panoramaItems = safeList(snapshot.getPanoramas()).stream()
                .map(panorama -> BoothReviewPanoramaItemDTO.builder()
                        .id(panorama.getId())
                        .name(panorama.getName())
                        .imageUrl(panorama.getImageUrl())
                        .orderIndex(panorama.getOrderIndex())
                        .isDefault(panorama.getIsDefault())
                        .hotspotCount(hotspotCountByPanoramaId.getOrDefault(panorama.getId(), 0))
                        .build())
                .toList();

        return BoothReviewContentOverviewDTO.builder()
                .panoramaCount(panoramaItems.size())
                .hotspotCount(safeList(snapshot.getHotspots()).size())
                .productCount(indexProductOverview(snapshot.getProductPlacements()).size())
                .mediaAssetCount(indexMediaOverview(snapshot.getMediaPlacements()).size())
                .panoramas(panoramaItems)
                .products(new ArrayList<>(indexProductOverview(snapshot.getProductPlacements()).values()))
                .mediaAssets(new ArrayList<>(indexMediaOverview(snapshot.getMediaPlacements()).values()))
                .build();
    }

    private Map<UUID, BoothReviewProductItemDTO> indexProductOverview(List<PlacementSnapshot> placements) {
        Map<UUID, BoothReviewProductItemDTO> productsById = new LinkedHashMap<>();
        for (PlacementSnapshot placement : safeList(placements)) {
            BoothReviewProductItemDTO item = productsById.computeIfAbsent(placement.getItemId(), key ->
                    BoothReviewProductItemDTO.builder()
                            .id(placement.getItemId())
                            .name(placement.getItemName())
                            .sku(placement.getSku())
                            .description(placement.getDescription())
                            .thumbnailUrl(placement.getThumbnailUrl())
                            .price(placement.getPrice())
                            .currency(placement.getCurrency())
                            .status(placement.getProductStatus())
                            .usageCount(0)
                            .placements(new ArrayList<>())
                            .build());
            item.setUsageCount(item.getUsageCount() + 1);
            item.getPlacements().add(toPlacementDTO(placement));
        }
        return productsById;
    }

    private Map<UUID, BoothReviewMediaItemDTO> indexMediaOverview(List<PlacementSnapshot> placements) {
        Map<UUID, BoothReviewMediaItemDTO> mediaAssetsById = new LinkedHashMap<>();
        for (PlacementSnapshot placement : safeList(placements)) {
            BoothReviewMediaItemDTO item = mediaAssetsById.computeIfAbsent(placement.getItemId(), key ->
                    BoothReviewMediaItemDTO.builder()
                            .id(placement.getItemId())
                            .name(placement.getItemName())
                            .type(placement.getMediaAssetType())
                            .url(placement.getUrl())
                            .mimeType(placement.getMimeType())
                            .fileSize(placement.getFileSize())
                            .usageCount(0)
                            .placements(new ArrayList<>())
                            .build());
            item.setUsageCount(item.getUsageCount() + 1);
            item.getPlacements().add(toPlacementDTO(placement));
        }
        return mediaAssetsById;
    }

    private BoothReviewContentPlacementDTO toPlacementDTO(PlacementSnapshot placement) {
        return BoothReviewContentPlacementDTO.builder()
                .panoramaId(placement.getPanoramaId())
                .panoramaName(placement.getPanoramaName())
                .hotspotId(placement.getHotspotId())
                .hotspotName(placement.getHotspotName())
                .build();
    }

    private BoothReviewSnapshot readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BoothReviewSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private BoothReviewChangeSummaryDTO readChangeSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BoothReviewChangeSummaryDTO.class);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
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

    @Transactional(readOnly = true)
    public BoothReviewRequestDetailDTO getLatestReviewRequestForOrganizer(User organizer, UUID exhibitionUuid, UUID boothId) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findForOrganizer(boothId, exhibitionUuid, organizer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        BoothReviewRequest request = boothReviewRequestRepository.findTopByBoothIdOrderBySubmittedAtDesc(booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_REVIEW_REQUEST_NOT_FOUND));
        return toDetail(request, booth);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothReviewRequestSummaryDTO> getReviewHistoryForOrganizer(
            User organizer,
            UUID exhibitionUuid,
            UUID boothId,
            Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findForOrganizer(boothId, exhibitionUuid, organizer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        Page<BoothReviewRequestSummaryDTO> page = boothReviewRequestRepository
                .findByBoothIdOrderBySubmittedAtDesc(booth.getId(), pageable)
                .map(this::toSummary);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getBoothsForOrganizer(
            User organizer,
            UUID exhibitionUuid,
            String keyword,
            BoothStatus status,
            Pageable pageable) {
        if (organizer == null || organizer.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Page<BoothResponseDTO> page = boothRepository
                .searchForOrganizer(exhibitionUuid, organizer.getId(), normalizeKeyword(keyword), status, pageable)
                .map(boothMapper::toBoothResponseDTO);
        return PageResponse.from(page);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoothReviewSnapshot {
        private BoothSnapshot booth;
        private List<PanoramaSnapshot> panoramas;
        private List<HotspotSnapshot> hotspots;
        private List<PlacementSnapshot> productPlacements;
        private List<PlacementSnapshot> mediaPlacements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BoothSnapshot {
        private String name;
        private String description;
        private String thumbnailUrl;
        private String backgroundMusicUrl;
        private String displayTemplateKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PanoramaSnapshot {
        private UUID id;
        private String name;
        private String imageUrl;
        private String imageKey;
        private Integer orderIndex;
        @JsonProperty("isDefault")
        private Boolean isDefault;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HotspotSnapshot {
        private UUID id;
        private String name;
        private HotspotType type;
        private UUID sourcePanoramaId;
        private String sourcePanoramaName;
        private UUID targetPanoramaId;
        private UUID productId;
        private String productName;
        private String productSku;
        private UUID mediaAssetId;
        private String mediaAssetName;
        private MediaAssetType mediaAssetType;
        private String mediaAssetUrl;
        private String infoText;
        private Double xPosition;
        private Double yPosition;
        private Double zPosition;
        private String iconStyle;
        private Double scale;
        private Integer zIndex;
        private HotspotMediaClickAction mediaClickAction;
        private HotspotInfoContentType infoContentType;
        private Double cornerTlX;
        private Double cornerTlY;
        private Double cornerTlZ;
        private Double cornerTrX;
        private Double cornerTrY;
        private Double cornerTrZ;
        private Double cornerBlX;
        private Double cornerBlY;
        private Double cornerBlZ;
        private Double cornerBrX;
        private Double cornerBrY;
        private Double cornerBrZ;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlacementSnapshot {
        private UUID itemId;
        private String itemName;
        private String sku;
        private String description;
        private String thumbnailUrl;
        private BigDecimal price;
        private String currency;
        private ProductStatus productStatus;
        private MediaAssetType mediaAssetType;
        private String url;
        private String mimeType;
        private Long fileSize;
        private UUID hotspotId;
        private String hotspotName;
        private UUID panoramaId;
        private String panoramaName;
    }
}
