package com.example.vex360.features.designrequest.services;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftStorageMetricsResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.enums.DesignDraftAssetSource;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignDraftStorageMetricsService {
    private final DesignDraftAssetRepository assetRepository;
    private final CompanyStorageService storageService;

    @Transactional(readOnly = true)
    public DesignDraftStorageMetricsResponseDTO calculate(DesignDraft draft) {
        long available = draft == null || draft.getDesignRequest() == null
                ? 0L
                : storageService.getUsage(draft.getDesignRequest().getCompany()).getAvailableBytes();
        if (draft == null || draft.getDesignRequest() == null) {
            return new DesignDraftStorageMetricsResponseDTO(0L, 0L, available);
        }

        Map<String, DesignDraftAsset> assetsByPublicId = new LinkedHashMap<>();
        assetRepository.findByDesignRequestId(draft.getDesignRequest().getId()).forEach(asset -> {
            if (asset.getPublicId() != null) {
                assetsByPublicId.putIfAbsent(asset.getPublicId(), asset);
            }
        });
        Map<String, Long> totalAssets = new LinkedHashMap<>();
        Map<String, Long> newAssets = new LinkedHashMap<>();
        draft.getPanoramas().stream()
                .flatMap(panorama -> panorama.getHotspots().stream())
                .forEach(hotspot -> addHotspotMediaSizes(hotspot, totalAssets, newAssets));
        return new DesignDraftStorageMetricsResponseDTO(
                totalAssets.values().stream().mapToLong(Long::longValue).sum(),
                newAssets.values().stream().mapToLong(Long::longValue).sum(),
                available);
    }

    private void addHotspotMediaSizes(
            DesignDraftHotspot hotspot,
            Map<String, Long> totalAssets,
            Map<String, Long> newAssets) {
        DesignDraftMediaAsset draftMedia = hotspot.getDesignDraftMediaAsset();
        if (draftMedia != null && draftMedia.getAsset() != null) {
            DesignDraftAsset asset = draftMedia.getAsset();
            addSize(totalAssets, asset.getPublicId(), asset.getFileSize());
            if (asset.getAssetSource() == DesignDraftAssetSource.UPLOADED) {
                addSize(newAssets, asset.getPublicId(), asset.getFileSize());
            }
            return;
        }
        MediaAsset mediaAsset = hotspot.getMediaAsset();
        if (mediaAsset != null) {
            String key = mediaAsset.getPublicId() == null
                    ? String.valueOf(mediaAsset.getId())
                    : mediaAsset.getPublicId();
            addSize(totalAssets, key, mediaAsset.getFileSize());
        }
    }

    private void addSize(Map<String, Long> sizes, String key, Long size) {
        if (key != null) {
            sizes.putIfAbsent(key, size == null ? 0L : size);
        }
    }
}
