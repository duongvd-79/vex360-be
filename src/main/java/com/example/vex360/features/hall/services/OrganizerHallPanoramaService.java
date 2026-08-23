package com.example.vex360.features.hall.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.hall.dtos.request.CreateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.request.UpdateHallPanoramaRequest;
import com.example.vex360.features.hall.dtos.response.HallPanoramaResponseDTO;
import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.hall.entities.HallPanorama;
import com.example.vex360.features.hall.mapper.HallSceneMapper;
import com.example.vex360.features.hall.repositories.HallHotspotRepository;
import com.example.vex360.features.hall.repositories.HallPanoramaRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizerHallPanoramaService {
    private final HallPanoramaRepository panoramaRepository;
    private final HallHotspotRepository hotspotRepository;
    private final ExhibitionHallService hallService;
    private final CloudService cloudService;
    private final PanoramaImageCleanupService imageCleanupService;
    private final HallSceneMapper sceneMapper;

    @Transactional(readOnly = true)
    public List<HallPanoramaResponseDTO> getPanoramas(User organizer, UUID exhibitionUuid) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return sceneMapper.toPanoramaResponses(
                panoramaRepository.findByHallIdOrderByOrderIndexAsc(hall.getId()));
    }

    @Transactional(readOnly = true)
    public HallPanoramaResponseDTO getPanorama(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return sceneMapper.toPanoramaResponse(findPanorama(hall, panoramaId));
    }

    @Transactional
    public HallPanoramaResponseDTO createPanorama(
            User organizer,
            UUID exhibitionUuid,
            CreateHallPanoramaRequest request,
            MultipartFile image) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        CloudinaryResponse uploaded = cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
        HallPanorama panorama = HallPanorama.builder()
                .hall(hall)
                .name(request.getName().trim())
                .imageUrl(uploaded.getUrl())
                .imageKey(uploaded.getPublicId())
                .fileSize(uploaded.getFileSize() == null ? image.getSize() : uploaded.getFileSize())
                .orderIndex(request.getOrderIndex() == null
                        ? panoramaRepository.findMaxOrderIndexByHallId(hall.getId()) + 1
                        : request.getOrderIndex())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        if (Boolean.TRUE.equals(panorama.getIsDefault())) {
            panoramaRepository.clearDefaultForHall(hall.getId());
        }

        try {
            return sceneMapper.toPanoramaResponse(panoramaRepository.saveAndFlush(panorama));
        } catch (RuntimeException exception) {
            cleanupNewUpload(uploaded.getPublicId(), exception);
            throw exception;
        }
    }

    @Transactional
    public HallPanoramaResponseDTO updatePanorama(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId,
            UpdateHallPanoramaRequest request,
            MultipartFile image) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallPanorama panorama = findPanoramaForUpdate(hall, panoramaId);
        CloudinaryResponse uploaded = null;
        String oldImageKey = null;

        if (request != null) {
            if (request.getName() != null) {
                panorama.setName(request.getName().trim());
            }
            if (request.getOrderIndex() != null) {
                panorama.setOrderIndex(request.getOrderIndex());
            }
            if (Boolean.TRUE.equals(request.getIsDefault())) {
                panoramaRepository.clearDefaultForHall(hall.getId());
                panorama.setIsDefault(true);
            } else if (Boolean.FALSE.equals(request.getIsDefault())) {
                panorama.setIsDefault(false);
            }
        }

        if (image != null && !image.isEmpty()) {
            uploaded = cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
            oldImageKey = panorama.getImageKey();
            panorama.setImageUrl(uploaded.getUrl());
            panorama.setImageKey(uploaded.getPublicId());
            panorama.setFileSize(uploaded.getFileSize() == null ? image.getSize() : uploaded.getFileSize());
        }

        try {
            HallPanorama saved = panoramaRepository.saveAndFlush(panorama);
            if (oldImageKey != null) {
                imageCleanupService.scheduleCleanup(oldImageKey);
            }
            return sceneMapper.toPanoramaResponse(saved);
        } catch (RuntimeException exception) {
            if (uploaded != null) {
                cleanupNewUpload(uploaded.getPublicId(), exception);
            }
            throw exception;
        }
    }

    @Transactional
    public HallPanoramaResponseDTO deletePanorama(
            User organizer,
            UUID exhibitionUuid,
            UUID panoramaId) {
        ExhibitionHall hall = hallService.findEditableHallForUpdate(organizer, exhibitionUuid);
        HallPanorama panorama = findPanoramaForUpdate(hall, panoramaId);
        HallPanoramaResponseDTO response = sceneMapper.toPanoramaResponse(panorama);
        String imageKey = panorama.getImageKey();

        hotspotRepository.deleteAll(hotspotRepository.findAllByTargetPanoramaId(panoramaId));
        hotspotRepository.flush();
        panoramaRepository.delete(panorama);
        panoramaRepository.flush();
        imageCleanupService.scheduleCleanup(imageKey);
        return response;
    }

    private HallPanorama findPanorama(ExhibitionHall hall, UUID panoramaId) {
        return panoramaRepository.findByIdAndHallId(panoramaId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_PANORAMA_NOT_FOUND));
    }

    private HallPanorama findPanoramaForUpdate(ExhibitionHall hall, UUID panoramaId) {
        return panoramaRepository.findByIdAndHallIdForUpdate(panoramaId, hall.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HALL_PANORAMA_NOT_FOUND));
    }

    private void cleanupNewUpload(String imageKey, RuntimeException originalException) {
        try {
            cloudService.delete(imageKey, "image");
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
