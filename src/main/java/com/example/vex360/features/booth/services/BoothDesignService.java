package com.example.vex360.features.booth.services;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothDesignService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PanoramaImageCleanupService panoramaImageCleanupService;

    @Transactional
    public Booth getCompanyBoothForUpdate(UUID boothId, UUID companyId) {
        return boothRepository.findCompanyBoothByIdForUpdate(boothId, companyId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Booth getCompanyBooth(UUID boothId, UUID companyId) {
        return boothRepository.findCompanyBoothById(boothId, companyId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Panorama getPanoramaForBooth(UUID panoramaId, UUID boothId) {
        return panoramaRepository.findByIdAndBoothId(panoramaId, boothId)
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MediaAsset getMediaAssetForCompany(UUID mediaAssetId, UUID companyId, MediaAssetType expectedType) {
        if (mediaAssetId == null) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndCompanyId(mediaAssetId, companyId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        if (expectedType != null && mediaAsset.getType() != expectedType) {
            throw new AppException(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID);
        }
        return mediaAsset;
    }

    @Transactional(readOnly = true)
    public Set<String> getPanoramaImageKeys(UUID boothId) {
        return panoramaRepository.findByBoothIdOrderByOrderIndexAsc(boothId).stream()
                .map(Panorama::getImageKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void replaceBoothContent(Booth booth, List<PanoramaDesign> panoramas) {
        List<Panorama> oldPanoramas = panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId());
        Set<String> oldImageKeys = oldPanoramas.stream()
                .map(Panorama::getImageKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toSet());
        List<UUID> oldPanoramaIds = oldPanoramas.stream().map(Panorama::getId).toList();
        if (!oldPanoramaIds.isEmpty()) {
            hotspotRepository.clearTargetsForPanoramas(oldPanoramaIds);
            booth.getPanoramas().removeAll(oldPanoramas);
            panoramaRepository.deleteAll(oldPanoramas);
        }

        Map<String, Panorama> appliedPanoramasByKey = new HashMap<>();
        List<PanoramaDesign> sortedPanoramas = panoramas.stream()
                .sorted(Comparator.comparing(PanoramaDesign::orderIndex))
                .toList();
        Set<String> reusedImageKeys = sortedPanoramas.stream()
                .map(PanoramaDesign::imageKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toSet());
        for (PanoramaDesign panoramaDesign : sortedPanoramas) {
            Panorama panorama = Panorama.builder()
                    .booth(booth)
                    .name(panoramaDesign.name())
                    .imageUrl(panoramaDesign.imageUrl())
                    .imageKey(panoramaDesign.imageKey())
                    .fileSize(panoramaDesign.fileSize())
                    .orderIndex(panoramaDesign.orderIndex())
                    .isDefault(panoramaDesign.isDefault())
                    .isTemplateDerived(Boolean.TRUE.equals(panoramaDesign.templateDerived()))
                    .build();
            Panorama saved = panoramaRepository.save(panorama);
            booth.getPanoramas().add(saved);
            appliedPanoramasByKey.put(panoramaDesign.clientKey(), saved);
        }

        for (PanoramaDesign panoramaDesign : sortedPanoramas) {
            Panorama source = appliedPanoramasByKey.get(panoramaDesign.clientKey());
            for (HotspotDesign hotspotDesign : panoramaDesign.hotspots()) {
                hotspotRepository.save(toHotspot(hotspotDesign, source, appliedPanoramasByKey));
            }
        }
        oldImageKeys.removeAll(reusedImageKeys);
        panoramaImageCleanupService.scheduleCleanup(oldImageKeys);
    }

    private Hotspot toHotspot(
            HotspotDesign design,
            Panorama source,
            Map<String, Panorama> appliedPanoramasByKey) {
        Hotspot hotspot = Hotspot.builder()
                .sourcePanorama(source)
                .type(design.type())
                .name(design.name())
                .targetPanorama(resolveTarget(design, appliedPanoramasByKey))
                .product(design.product())
                .mediaAsset(design.mediaAsset())
                .infoText(design.infoText())
                .xPosition(design.xPosition())
                .yPosition(design.yPosition())
                .zPosition(design.zPosition())
                .iconStyle(design.iconStyle())
                .scale(design.scale())
                .zIndex(design.zIndex())
                .mediaClickAction(design.mediaClickAction())
                .infoContentType(design.infoContentType())
                .build();
        hotspot.setCornerTlX(design.cornerTlX());
        hotspot.setCornerTlY(design.cornerTlY());
        hotspot.setCornerTlZ(design.cornerTlZ());
        hotspot.setCornerTrX(design.cornerTrX());
        hotspot.setCornerTrY(design.cornerTrY());
        hotspot.setCornerTrZ(design.cornerTrZ());
        hotspot.setCornerBlX(design.cornerBlX());
        hotspot.setCornerBlY(design.cornerBlY());
        hotspot.setCornerBlZ(design.cornerBlZ());
        hotspot.setCornerBrX(design.cornerBrX());
        hotspot.setCornerBrY(design.cornerBrY());
        hotspot.setCornerBrZ(design.cornerBrZ());
        return hotspot;
    }

    private Panorama resolveTarget(HotspotDesign design, Map<String, Panorama> appliedPanoramasByKey) {
        if (design.targetDraftPanoramaKey() != null) {
            return appliedPanoramasByKey.get(design.targetDraftPanoramaKey());
        }
        return null;
    }

    public record PanoramaDesign(
            String clientKey,
            String name,
            String imageUrl,
            String imageKey,
            Long fileSize,
            Integer orderIndex,
            Boolean isDefault,
            Boolean templateDerived,
            List<HotspotDesign> hotspots) {
    }

    public record HotspotDesign(
            HotspotType type,
            String name,
            String targetDraftPanoramaKey,
            Product product,
            MediaAsset mediaAsset,
            String infoText,
            Double xPosition,
            Double yPosition,
            Double zPosition,
            String iconStyle,
            Double scale,
            Integer zIndex,
            HotspotMediaClickAction mediaClickAction,
            HotspotInfoContentType infoContentType,
            Double cornerTlX,
            Double cornerTlY,
            Double cornerTlZ,
            Double cornerTrX,
            Double cornerTrY,
            Double cornerTrZ,
            Double cornerBlX,
            Double cornerBlY,
            Double cornerBlZ,
            Double cornerBrX,
            Double cornerBrY,
            Double cornerBrZ) {
    }

    @Transactional(readOnly = true)
    public boolean existsPanoramaByImageKey(String imageKey) {
        return panoramaRepository.existsByImageKey(imageKey);
    }

    @Transactional(readOnly = true)
    public boolean existsBoothByThumbnailOrMusic(String publicId) {
        return boothRepository.existsByThumbnailPublicIdOrBackgroundMusicPublicId(publicId, publicId);
    }

    @Transactional(readOnly = true)
    public long countPanoramasByBoothId(UUID boothId) {
        return panoramaRepository.countByBoothId(boothId);
    }

    @Transactional(readOnly = true)
    public List<Panorama> findPanoramaDetailsByBoothId(UUID boothId) {
        return panoramaRepository.findDetailsByBoothId(boothId);
    }

    @Transactional(readOnly = true)
    public boolean existsInactiveHotspotProductInBooth(UUID boothId) {
        return hotspotRepository.existsBySourcePanoramaBoothIdAndProductStatusNot(boothId, ProductStatus.ACTIVE);
    }

    @Transactional
    public boolean existsInactiveHotspotProductInBoothForUpdate(UUID boothId) {
        return hotspotRepository.findProductsByBoothIdForUpdate(boothId).stream()
                .anyMatch(product -> product.getStatus() != ProductStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<UUID> findDistinctMediaAssetIdsByBoothId(UUID boothId, UUID excludedHotspotId) {
        return hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(boothId, excludedHotspotId)
                .stream().map(MediaAsset::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findDistinctProductIdsByBoothId(UUID boothId, UUID excludedHotspotId) {
        return hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(boothId, excludedHotspotId);
    }

    @Transactional(readOnly = true)
    public Optional<Booth> findCompanyBoothById(UUID boothId, UUID companyId) {
        if (boothId == null || companyId == null) {
            return Optional.empty();
        }
        return boothRepository.findCompanyBoothById(boothId, companyId);
    }
}
