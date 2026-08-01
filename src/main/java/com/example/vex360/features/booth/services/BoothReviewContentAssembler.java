package com.example.vex360.features.booth.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.dtos.response.BoothReviewChangeSummaryDTO;
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
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoothReviewContentAssembler {
    private final BoothMapper boothMapper;

    public OrganizerBoothContentOverviewDTO toOrganizerContentOverview(
            Booth booth,
            BoothReviewRequest pendingRequest) {
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

    public BoothReviewRequestSummaryDTO toRequestSummary(
            BoothReviewRequest request,
            BoothReviewChangeSummaryDTO changeSummary) {
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
                .canceledAt(request.getCanceledAt()).cancellationReason(request.getCancellationReason())
                .changeSummary(changeSummary)
                .build();
    }

    public BoothReviewContentOverviewDTO toContentOverview(Booth booth) {
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
                    .fileSize(panorama.getFileSize())
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
                        ? null
                        : company.getOwnerUser().getFullName())
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

    private List<Panorama> sortedPanoramas(Booth booth) {
        if (booth.getPanoramas() == null) {
            return List.of();
        }
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
        if (registration == null) {
            return null;
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        return exhibitionPackage == null ? null : exhibitionPackage.getExhibition();
    }
}
