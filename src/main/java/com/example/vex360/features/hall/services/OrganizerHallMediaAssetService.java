package com.example.vex360.features.hall.services;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateMediaAssetRequest;
import com.example.vex360.features.booth.dtos.request.RenameMediaAssetRequest;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallItemRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizerHallMediaAssetService {
    private final ExhibitionHallService hallService;
    private final ExhibitorMediaAssetService mediaAssetService;
    private final DesignAssetReferenceService assetReferenceService;
    private final HallHotspotRepository hotspotRepository;
    private final HallItemRepository itemRepository;

    @Transactional(readOnly = true)
    public PageResponse<MediaAssetResponseDTO> getMediaAssets(
            User organizer,
            UUID exhibitionUuid,
            String filterType,
            Pageable pageable) {
        hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return mediaAssetService.getMediaAssets(organizer, filterType, pageable);
    }

    @Transactional
    public MediaAssetResponseDTO createMediaAsset(
            User organizer,
            UUID exhibitionUuid,
            CreateMediaAssetRequest request,
            MultipartFile file) {
        hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        return mediaAssetService.createMediaAsset(organizer, request, file);
    }

    @Transactional
    public MediaAssetResponseDTO renameMediaAsset(
            User organizer,
            UUID exhibitionUuid,
            UUID assetId,
            RenameMediaAssetRequest request) {
        hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        return mediaAssetService.renameAsset(organizer, assetId, request);
    }

    @Transactional
    public MediaAssetResponseDTO deleteMediaAsset(
            User organizer,
            UUID exhibitionUuid,
            UUID assetId) {
        hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        if (hotspotRepository.existsByMediaAssetId(assetId)
                || itemRepository.existsByMediaAssetId(assetId)) {
            throw new AppException(ErrorCode.MEDIA_ASSET_IN_USE);
        }
        return assetReferenceService.deleteMediaAsset(organizer, assetId);
    }
}
