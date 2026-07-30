package com.example.vex360.features.designrequest.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignRequestMediaAssetService {
    private final DesignRequestMediaAssetRepository requestMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final HotspotRepository hotspotRepository;

    @Transactional
    public void initializeAllowlist(DesignRequest request, List<UUID> selectedMediaAssetIds) {
        List<UUID> selectedIds = distinctIds(selectedMediaAssetIds);
        List<MediaAsset> selected = loadCompanyMediaAssets(request, selectedIds);
        Map<UUID, MediaAsset> mediaAssets = new HashMap<>();
        selected.forEach(mediaAsset -> mediaAssets.put(mediaAsset.getId(), mediaAsset));

        Set<UUID> requiredIds = new HashSet<>();
        if (request.getMode() == DesignRequestMode.REDESIGN) {
            for (MediaAsset mediaAsset : hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(
                    request.getBooth().getId(), null)) {
                if (!request.getCompany().getId().equals(mediaAsset.getCompany().getId())) {
                    throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
                }
                mediaAssets.put(mediaAsset.getId(), mediaAsset);
                requiredIds.add(mediaAsset.getId());
            }
        }

        mediaAssets.values().forEach(mediaAsset -> request.getMediaAssets().add(DesignRequestMediaAsset.builder()
                .designRequest(request)
                .mediaAsset(mediaAsset)
                .requiredFromBaseline(requiredIds.contains(mediaAsset.getId()))
                .build()));
    }

    @Transactional(readOnly = true)
    public void assertMediaAssetAllowed(DesignRequest request, UUID mediaAssetId) {
        if (!requestMediaAssetRepository.existsByDesignRequestIdAndMediaAssetId(request.getId(), mediaAssetId)) {
            throw new AppException(ErrorCode.DESIGN_MEDIA_ASSET_NOT_ALLOWED);
        }
    }

    private List<MediaAsset> loadCompanyMediaAssets(DesignRequest request, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<MediaAsset> mediaAssets = mediaAssetRepository.findByIdInAndCompanyId(ids, request.getCompany().getId());
        if (mediaAssets.size() != ids.size()) {
            throw new AppException(ErrorCode.INVALID_MEDIA_ASSET);
        }
        return mediaAssets;
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
            }
            distinct.add(id);
        }
        return List.copyOf(distinct);
    }
}
