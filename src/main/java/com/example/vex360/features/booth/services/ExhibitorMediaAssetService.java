package com.example.vex360.features.booth.services;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateMediaAssetRequest;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorMediaAssetService {
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "video/mp4");

    private final MediaAssetRepository mediaAssetRepository;
    private final HotspotRepository hotspotRepository;
    private final CompanyService companyService;
    private final CompanyStorageService companyStorageService;
    private final CloudService cloudService;
    private final BoothMapper boothMapper;
    private final DesignAssetReferenceService assetReferenceService;

    @Transactional(readOnly = true)
    public PageResponse<MediaAssetResponseDTO> getMediaAssets(User currentUser, Pageable pageable) {
        Company company = getCompanyForCurrentUser(currentUser);
        Page<MediaAssetResponseDTO> assets = mediaAssetRepository.findByCompanyId(company.getId(), pageable)
                .map(boothMapper::toMediaAssetResponseDTO);
        return PageResponse.from(assets);
    }

    @Transactional
    public MediaAssetResponseDTO createMediaAsset(
            User currentUser,
            CreateMediaAssetRequest request,
            MultipartFile file) {
        Company company = getCompanyForCurrentUser(currentUser);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_MEDIA_ASSET);
        }
        validateMediaFile(file);

        companyStorageService.checkQuota(company, file.getSize());

        CloudinaryResponse upload = cloudService.upload(file);
        String mimeType = upload.getFileType() == null ? normalizeMimeType(file) : upload.getFileType();
        Long fileSize = upload.getFileSize() == null ? file.getSize() : upload.getFileSize();
        MediaAsset mediaAsset = MediaAsset.builder()
                .company(company)
                .name(request.getName().trim())
                .type(resolveMediaAssetType(mimeType))
                .url(upload.getUrl())
                .publicId(upload.getPublicId())
                .mimeType(mimeType)
                .fileSize(fileSize)
                .build();

        MediaAssetResponseDTO result = boothMapper.toMediaAssetResponseDTO(mediaAssetRepository.save(mediaAsset));
        companyStorageService.addUsage(company, fileSize); // ← thêm dòng này SAU khi lưu
        return result;

    }

    @Transactional
    public MediaAssetResponseDTO deleteMediaAsset(User currentUser, UUID assetId) {
        Company company = getCompanyForCurrentUser(currentUser);
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndCompanyId(assetId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        if (hotspotRepository.existsByMediaAssetId(assetId)) {
            throw new AppException(ErrorCode.INVALID_MEDIA_ASSET);
        }

        MediaAssetResponseDTO response = boothMapper.toMediaAssetResponseDTO(mediaAsset);
        mediaAssetRepository.delete(mediaAsset);
        companyStorageService.deductUsage(company, mediaAsset.getFileSize() != null ? mediaAsset.getFileSize() : 0L);
        assetReferenceService.scheduleCleanup(mediaAsset.getPublicId(), toResourceType(mediaAsset.getType()));
        return response;
    }

    private void validateMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty() || !ALLOWED_MEDIA_TYPES.contains(normalizeMimeType(file))) {
            throw new AppException(ErrorCode.INVALID_MEDIA_ASSET);
        }
    }

    private MediaAssetType resolveMediaAssetType(String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return MediaAssetType.VIDEO;
        }
        return MediaAssetType.IMAGE;
    }

    private String toResourceType(MediaAssetType type) {
        return type == MediaAssetType.VIDEO ? "video" : "image";
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
    }

    private String normalizeMimeType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }
}
