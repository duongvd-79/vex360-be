package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.UpdateBoothRequest;
import com.example.vex360.features.booth.dtos.response.BoothBenefitUsageResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorBoothService {
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_BACKGROUND_MUSIC_TYPES = Set.of("audio/mpeg", "audio/mp3");
    private static final String BACKGROUND_MUSIC_FOLDER = "booth-background-music";
    private static final long BACKGROUND_MUSIC_MAX_SIZE_MB = 10;
    private static final String CLOUDINARY_AUDIO_RESOURCE_TYPE = "video";

    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CompanyService companyService;
    private final CloudService cloudService;
    private final BoothMapper boothMapper;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final PanoramaImageCleanupService assetCleanupService;

    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getBooths(User currentUser, Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<BoothResponseDTO> booths = boothRepository.findCompanyBooths(company.getId(), pageable)
                .map(boothMapper::toBoothResponseDTO);
        return PageResponse.from(booths);
    }

    @Transactional(readOnly = true)
    public BoothResponseDTO getBoothById(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return boothMapper.toBoothResponseDTO(getBoothForCompany(boothId, company));
    }

    @Transactional(readOnly = true)
    public BoothBenefitUsageResponseDTO getBoothBenefitUsage(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);

        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_FOUND);
        }

        long usedPanoramas = panoramaRepository.countByBoothId(boothId);
        long usedHotspots = hotspotRepository.countBySourcePanoramaBoothId(boothId);

        List<UUID> productIds = hotspotRepository.findDistinctProductIdsByBoothIdExcludingHotspot(boothId, null);
        long usedProducts = productIds != null ? productIds.size() : 0L;

        List<MediaAsset> mediaAssets = hotspotRepository.findDistinctMediaAssetsByBoothIdExcludingHotspot(boothId, null);
        long usedMediaVideos = 0L;
        if (mediaAssets != null) {
            usedMediaVideos = mediaAssets.stream()
                    .filter(m -> m != null && m.getType() == MediaAssetType.VIDEO && m.getId() != null)
                    .map(MediaAsset::getId)
                    .distinct()
                    .count();
        }

        return BoothBenefitUsageResponseDTO.builder()
                .packageName(registration.getPackageNameSnapshot())
                .panoramas(BoothBenefitUsageResponseDTO.UsageQuota.builder()
                        .used(usedPanoramas)
                        .max(registration.getMaxPanoramasPerBoothSnapshot() != null ? registration.getMaxPanoramasPerBoothSnapshot() : 0)
                        .build())
                .hotspots(BoothBenefitUsageResponseDTO.UsageQuota.builder()
                        .used(usedHotspots)
                        .max(registration.getMaxHotspotsPerBoothSnapshot() != null ? registration.getMaxHotspotsPerBoothSnapshot() : 0)
                        .build())
                .products(BoothBenefitUsageResponseDTO.UsageQuota.builder()
                        .used(usedProducts)
                        .max(registration.getMaxProductsPerBoothSnapshot() != null ? registration.getMaxProductsPerBoothSnapshot() : 0)
                        .build())
                .mediaVideos(BoothBenefitUsageResponseDTO.UsageQuota.builder()
                        .used(usedMediaVideos)
                        .max(registration.getMaxEmbeddedVideosPerBoothSnapshot() != null ? registration.getMaxEmbeddedVideosPerBoothSnapshot() : 0)
                        .build())
                .build();
    }

    @Transactional
    public BoothResponseDTO updateBooth(
            User currentUser,
            UUID boothId,
            UpdateBoothRequest request,
            MultipartFile thumbnail,
            MultipartFile backgroundMusic) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        boothReviewPolicyService.assertMetadataEditable(booth);

        if (request != null) {
            updateMetadata(booth, request);
        }
        String oldThumbnailPublicId = booth.getThumbnailPublicId();
        CloudinaryResponse thumbnailUpload = thumbnail != null && !thumbnail.isEmpty()
                ? replaceThumbnail(booth, thumbnail)
                : null;

        String oldBackgroundMusicPublicId = booth.getBackgroundMusicPublicId();
        CloudinaryResponse backgroundMusicUpload = null;
        if (backgroundMusic != null) {
            backgroundMusicUpload = replaceBackgroundMusic(booth, backgroundMusic);
        }

        Booth savedBooth;
        try {
            savedBooth = boothRepository.save(booth);
            boothRepository.flush();
        } catch (RuntimeException exception) {
            if (backgroundMusicUpload != null) {
                cleanupUploadedBackgroundMusic(backgroundMusicUpload.getPublicId(), exception);
            }
            throw exception;
        }

        if (thumbnailUpload != null && hasText(oldThumbnailPublicId)) {
            assetCleanupService.scheduleCleanup(oldThumbnailPublicId, "image");
        }
        if (backgroundMusicUpload != null && hasText(oldBackgroundMusicPublicId)) {
            assetCleanupService.scheduleCleanup(oldBackgroundMusicPublicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
        }

        return boothMapper.toBoothResponseDTO(savedBooth);
    }

    @Transactional
    public BoothResponseDTO deleteBackgroundMusic(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = getBoothForCompany(boothId, company);
        boothReviewPolicyService.assertMetadataEditable(booth);

        String publicId = booth.getBackgroundMusicPublicId();
        if (!hasText(publicId)) {
            return boothMapper.toBoothResponseDTO(booth);
        }

        booth.setBackgroundMusicUrl(null);
        booth.setBackgroundMusicPublicId(null);
        booth.setBackgroundMusicFileName(null);
        booth.setBackgroundMusicFileSize(null);
        Booth savedBooth = boothRepository.save(booth);
        boothRepository.flush();
        assetCleanupService.scheduleCleanup(publicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
        return boothMapper.toBoothResponseDTO(savedBooth);
    }

    private void updateMetadata(Booth booth, UpdateBoothRequest request) {
        if (booth.getStatus() == com.example.vex360.features.booth.enums.BoothStatus.DESIGN_REQUEST_PENDING
                && request.getDisplayTemplateKey() != null) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new AppException(ErrorCode.BOOTH_NAME_REQUIRED);
            }
            booth.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            booth.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        if (request.getDisplayTemplateKey() != null) {
            booth.setDisplayTemplateKey(request.getDisplayTemplateKey().isBlank()
                    ? "classic"
                    : request.getDisplayTemplateKey().trim());
        }
    }

    private CloudinaryResponse replaceThumbnail(Booth booth, MultipartFile thumbnail) {
        validateThumbnail(thumbnail);
        CloudinaryResponse upload = cloudService.upload(thumbnail);
        booth.setThumbnailUrl(upload.getUrl());
        booth.setThumbnailPublicId(upload.getPublicId());
        return upload;
    }

    private CloudinaryResponse replaceBackgroundMusic(Booth booth, MultipartFile backgroundMusic) {
        validateBackgroundMusic(backgroundMusic);
        CloudinaryResponse upload = cloudService.uploadToFolder(backgroundMusic, BACKGROUND_MUSIC_FOLDER);
        booth.setBackgroundMusicUrl(upload.getUrl());
        booth.setBackgroundMusicPublicId(upload.getPublicId());
        booth.setBackgroundMusicFileName(backgroundMusic.getOriginalFilename());
        booth.setBackgroundMusicFileSize(upload.getFileSize() == null
                ? backgroundMusic.getSize()
                : upload.getFileSize());
        return upload;
    }

    private Booth getBoothForCompany(UUID boothId, Company company) {
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()
                || !ALLOWED_THUMBNAIL_TYPES.contains(normalizeMimeType(thumbnail))) {
            throw new AppException(ErrorCode.BOOTH_THUMBNAIL_INVALID);
        }
    }

    private void validateBackgroundMusic(MultipartFile backgroundMusic) {
        String extension = FileUploadUtils.getFileExtension(backgroundMusic == null
                ? null
                : backgroundMusic.getOriginalFilename());
        if (backgroundMusic == null || backgroundMusic.isEmpty()
                || extension == null || !"mp3".equalsIgnoreCase(extension)
                || !ALLOWED_BACKGROUND_MUSIC_TYPES.contains(normalizeMimeType(backgroundMusic))) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        FileUploadUtils.validateFileSize(backgroundMusic, BACKGROUND_MUSIC_MAX_SIZE_MB);
    }

    private void cleanupUploadedBackgroundMusic(String publicId, RuntimeException originalException) {
        try {
            cloudService.delete(publicId, CLOUDINARY_AUDIO_RESOURCE_TYPE);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private String normalizeMimeType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
