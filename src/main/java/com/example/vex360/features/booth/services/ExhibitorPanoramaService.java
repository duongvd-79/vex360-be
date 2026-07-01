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
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.PanoramaStorageService.StoredPanoramaFile;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorPanoramaService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CompanyRepository companyRepository;
    private final PanoramaStorageService panoramaStorageService;
    private final BoothMapper boothMapper;

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
        if (request == null || isBlank(request.getName())) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }

        StoredPanoramaFile storedFile = panoramaStorageService.store(image);
        Panorama panorama = Panorama.builder()
                .booth(booth)
                .name(request.getName().trim())
                .imageUrl(storedFile.imageUrl())
                .imageKey(storedFile.imageKey())
                .orderIndex(request.getOrderIndex() == null ? nextOrderIndex(booth.getId()) : request.getOrderIndex())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        if (Boolean.TRUE.equals(panorama.getIsDefault())) {
            panoramaRepository.clearDefaultForBooth(booth.getId());
        } else if (panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()).isEmpty()) {
            panorama.setIsDefault(true);
        }

        return boothMapper.toPanoramaResponseDTO(panoramaRepository.save(panorama));
    }

    @Transactional
    public PanoramaResponseDTO updatePanorama(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            UpdateExhibitorPanoramaRequest request,
            MultipartFile image) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        Panorama panorama = getPanoramaForBooth(panoramaId, booth);

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
            StoredPanoramaFile storedFile = panoramaStorageService.store(image);
            panoramaStorageService.delete(panorama.getImageKey());
            panorama.setImageUrl(storedFile.imageUrl());
            panorama.setImageKey(storedFile.imageKey());
        }

        return boothMapper.toPanoramaResponseDTO(panoramaRepository.save(panorama));
    }

    @Transactional
    public PanoramaResponseDTO deletePanorama(User currentUser, UUID boothId, UUID panoramaId) {
        Booth booth = getBoothForCurrentUser(currentUser, boothId);
        Panorama panorama = getPanoramaForBooth(panoramaId, booth);
        if (hotspotRepository.existsByTargetPanoramaId(panoramaId)) {
            throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
        }

        PanoramaResponseDTO response = boothMapper.toPanoramaResponseDTO(panorama);
        panoramaRepository.delete(panorama);
        panoramaStorageService.delete(panorama.getImageKey());
        return response;
    }

    private int nextOrderIndex(UUID boothId) {
        return panoramaRepository.findByBoothIdOrderByOrderIndexAsc(boothId).size();
    }

    private Panorama getPanoramaForBooth(UUID panoramaId, Booth booth) {
        return panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private Booth getBoothForCurrentUser(User currentUser, UUID boothId) {
        Company company = getCompanyForCurrentUser(currentUser);
        return boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
