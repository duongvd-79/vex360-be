package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorPanoramaService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;
    private final CloudService cloudService;
    private final PanoramaImageCleanupService panoramaImageCleanupService;
    private final BoothMapper boothMapper;
    private final BoothBenefitGuardService boothBenefitGuardService;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final DesignDraftAssetRepository designDraftAssetRepository;

    @Transactional(readOnly = true)
    public List<PanoramaResponseDTO> getPanoramas(User currentUser, UUID boothId) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        return boothMapper.toPanoramaResponseDTOs(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()));
    }

    @Transactional(readOnly = true)
    public PanoramaResponseDTO getPanoramaById(User currentUser, UUID boothId, UUID panoramaId) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        return boothMapper.toPanoramaResponseDTO(getPanoramaForBooth(panoramaId, booth));
    }

    @Transactional
    public PanoramaResponseDTO createPanorama(
            User currentUser,
            UUID boothId,
            CreateExhibitorPanoramaRequest request,
            MultipartFile image) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        boothReviewPolicyService.assertEditable(booth);
        if (request == null || isBlank(request.getName())) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }
        boothBenefitGuardService.assertCanAddPanorama(booth);

        CloudinaryResponse uploaded = cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
        long fileSize = uploaded.getFileSize() == null ? image.getSize() : uploaded.getFileSize();
        Panorama panorama = Panorama.builder()
                .booth(booth)
                .name(request.getName().trim())
                .imageUrl(uploaded.getUrl())
                .imageKey(uploaded.getPublicId())
                .fileSize(fileSize)
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
            Panorama saved = panoramaRepository.saveAndFlush(panorama);
            companyStorageService.addUsage(booth.getCompany(), fileSize);
            return boothMapper.toPanoramaResponseDTO(saved);
        } catch (RuntimeException exception) {
            cleanupNewUpload(uploaded.getPublicId(), exception);
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
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        boothReviewPolicyService.assertEditable(booth);
        Panorama panorama = getPanoramaForBoothForUpdate(panoramaId, booth);
        CloudinaryResponse uploaded = null;
        String oldImageKey = null;
        long oldFileSize = 0L;
        long newFileSize = 0L;

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
            newFileSize = uploaded.getFileSize() == null ? image.getSize() : uploaded.getFileSize();
            oldFileSize = billableFileSize(panorama);
            panorama.setFileSize(newFileSize);
            panorama.setIsTemplateDerived(false);
        }

        try {
            if (uploaded != null) {
                companyStorageService.reconcileUsage(booth.getCompany(), oldFileSize, newFileSize, 0L);
            }
            Panorama saved = panoramaRepository.saveAndFlush(panorama);
            if (oldImageKey != null) {
                panoramaImageCleanupService.scheduleCleanup(oldImageKey);
            }
            return boothMapper.toPanoramaResponseDTO(saved);
        } catch (RuntimeException exception) {
            if (uploaded != null) {
                cleanupNewUpload(uploaded.getPublicId(), exception);
            }
            throw exception;
        }
    }

    @Transactional
    public PanoramaResponseDTO deletePanorama(User currentUser, UUID boothId, UUID panoramaId) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        boothReviewPolicyService.assertEditable(booth);
        Panorama panorama = getPanoramaForBoothForUpdate(panoramaId, booth);

        PanoramaResponseDTO response = boothMapper.toPanoramaResponseDTO(panorama);
        String oldImageKey = panorama.getImageKey();
        deleteIncomingHotspots(List.of(panoramaId));
        panoramaRepository.delete(panorama);
        panoramaRepository.flush();
        if (!Boolean.TRUE.equals(panorama.getIsTemplateDerived())) {
            companyStorageService.deductUsage(booth.getCompany(), billableFileSize(panorama));
        }
        panoramaImageCleanupService.scheduleCleanup(oldImageKey);
        return response;
    }

    @Transactional
    public void deleteAllPanoramas(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothByIdForUpdate(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        boothReviewPolicyService.assertEditable(booth);

        List<Panorama> panoramas = panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId());
        if (panoramas.isEmpty()) {
            throw new AppException(ErrorCode.PANORAMA_NOT_FOUND);
        }

        List<UUID> panoramaIds = panoramas.stream().map(Panorama::getId).toList();
        Set<String> imageKeys = panoramas.stream()
                .map(Panorama::getImageKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toSet());

        deleteIncomingHotspots(panoramaIds);
        panoramaRepository.deleteAll(panoramas);
        panoramaRepository.flush();
        long releasedBytes = panoramas.stream()
                .filter(panorama -> !Boolean.TRUE.equals(panorama.getIsTemplateDerived()))
                .mapToLong(this::billableFileSize)
                .sum();
        if (releasedBytes > 0) {
            companyStorageService.deductUsage(company, releasedBytes);
        }
        panoramaImageCleanupService.scheduleCleanup(imageKeys);
    }

    private void deleteIncomingHotspots(List<UUID> panoramaIds) {
        hotspotRepository.deleteAll(hotspotRepository.findAllByTargetPanoramaIdIn(panoramaIds));
        hotspotRepository.flush();
    }

    private int nextOrderIndex(UUID boothId) {
        return panoramaRepository.findByBoothIdOrderByOrderIndexAsc(boothId).size();
    }

    private long billableFileSize(Panorama panorama) {
        if (Boolean.TRUE.equals(panorama.getIsTemplateDerived())) {
            return 0L;
        }
        if (panorama.getFileSize() != null) {
            return panorama.getFileSize();
        }
        long backfilledSize = designDraftAssetRepository
                .findFirstByPublicIdOrderByCreatedAtDesc(panorama.getImageKey())
                .map(asset -> asset.getFileSize() == null ? 0L : asset.getFileSize())
                .orElse(0L);
        panorama.setFileSize(backfilledSize);
        return backfilledSize;
    }

    private Panorama getPanoramaForBooth(UUID panoramaId, Booth booth) {
        return panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private Panorama getPanoramaForBoothForUpdate(UUID panoramaId, Booth booth) {
        return panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private void cleanupNewUpload(String imageKey, RuntimeException originalException) {
        try {
            cloudService.delete(imageKey, "image");
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private Booth getBoothForCurrentUser(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
