package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothTemplatePanoramaService {
    private static final String IMAGE_RESOURCE_TYPE = "image";

    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CloudService cloudService;
    private final PanoramaImageCleanupService panoramaImageCleanupService;
    private final BoothMapper boothMapper;

    @Transactional(readOnly = true)
    public List<PanoramaResponseDTO> getPanoramas(User currentUser, UUID boothId) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        return boothMapper.toPanoramaResponseDTOs(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()));
    }

    @Transactional
    public PanoramaResponseDTO createPanorama(
            User currentUser,
            UUID boothId,
            CreateExhibitorPanoramaRequest request,
            MultipartFile image) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateByIdForUpdate(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        if (request == null || isBlank(request.getName())) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }

        CloudinaryResponse uploaded = cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
        Panorama panorama = Panorama.builder()
                .booth(booth)
                .name(request.getName().trim())
                .imageUrl(uploaded.getUrl())
                .imageKey(uploaded.getPublicId())
                .fileSize(uploaded.getFileSize() == null ? image.getSize() : uploaded.getFileSize())
                .orderIndex(request.getOrderIndex() == null ? nextOrderIndex(booth.getId()) : request.getOrderIndex())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .isTemplateDerived(false)
                .build();

        if (Boolean.TRUE.equals(panorama.getIsDefault())) {
            panoramaRepository.clearDefaultForBooth(booth.getId());
        } else if (panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()).isEmpty()) {
            panorama.setIsDefault(true);
        }

        try {
            return boothMapper.toPanoramaResponseDTO(panoramaRepository.saveAndFlush(panorama));
        } catch (RuntimeException exception) {
            cleanupUploadedImage(uploaded.getPublicId());
            throw exception;
        }
    }

    @Transactional
    public PanoramaResponseDTO updatePanorama(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            UpdateExhibitorPanoramaRequest request,
            MultipartFile image) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateByIdForUpdate(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

        Panorama panorama = panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

        CloudinaryResponse uploaded = null;
        String oldImageKey = null;

        if (request != null) {
            if (request.getName() != null) {
                if (request.getName().isBlank()) {
                    throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
                }
                panorama.setName(request.getName().trim());
            }
            if (request.getOrderIndex() != null) {
                panorama.setOrderIndex(request.getOrderIndex());
            }
            if (Boolean.TRUE.equals(request.getIsDefault())) {
                panoramaRepository.clearDefaultForBooth(booth.getId());
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
            panorama.setIsTemplateDerived(false);
        }

        try {
            Panorama saved = panoramaRepository.saveAndFlush(panorama);
            panoramaImageCleanupService.scheduleCleanup(oldImageKey);
            return boothMapper.toPanoramaResponseDTO(saved);
        } catch (RuntimeException exception) {
            if (uploaded != null) {
                cleanupUploadedImage(uploaded.getPublicId());
            }
            throw exception;
        }
    }

    @Transactional
    public PanoramaResponseDTO deletePanorama(User currentUser, UUID boothId, UUID panoramaId) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateByIdForUpdate(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

        Panorama panorama = panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

        PanoramaResponseDTO response = boothMapper.toPanoramaResponseDTO(panorama);
        String oldImageKey = panorama.getImageKey();
        hotspotRepository.deleteAll(hotspotRepository.findAllByTargetPanoramaIdIn(List.of(panoramaId)));
        hotspotRepository.flush();
        panoramaRepository.delete(panorama);
        panoramaRepository.flush();
        panoramaImageCleanupService.scheduleCleanup(oldImageKey);
        return response;
    }

    private int nextOrderIndex(UUID boothId) {
        return panoramaRepository.findByBoothIdOrderByOrderIndexAsc(boothId).size();
    }

    private void cleanupUploadedImage(String imageKey) {
        if (isBlank(imageKey)) {
            return;
        }
        try {
            cloudService.delete(imageKey, IMAGE_RESOURCE_TYPE);
        } catch (RuntimeException ignored) {
            // Preserve the original persistence failure if cleanup also fails.
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
