package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothTemplateHotspotService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final BoothMapper boothMapper;

    @Transactional(readOnly = true)
    public List<HotspotResponseDTO> getHotspots(User currentUser, UUID boothId, UUID panoramaId) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        Panorama panorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
        return boothMapper
                .toHotspotResponseDTOs(hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(panorama.getId()));
    }

    @Transactional
    public HotspotResponseDTO createHotspot(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            CreateBoothTemplateHotspotRequest request) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = getEditableTemplate(boothId);
        Panorama sourcePanorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
        Panorama targetPanorama = panoramaRepository.findByIdAndBoothId(request.getTargetPanoramaId(), booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

        Hotspot hotspot = Hotspot.builder()
                .type(HotspotType.NAV)
                .name(request.getName().trim())
                .sourcePanorama(sourcePanorama)
                .targetPanorama(targetPanorama)
                .xPosition(request.getXPosition())
                .yPosition(request.getYPosition())
                .zPosition(request.getZPosition())
                .build();
        return boothMapper.toHotspotResponseDTO(hotspotRepository.save(hotspot));
    }

    @Transactional
    public HotspotResponseDTO updateHotspot(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            UUID hotspotId,
            UpdateBoothTemplateHotspotRequest request) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = getEditableTemplate(boothId);
        Panorama sourcePanorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
        Hotspot hotspot = hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, sourcePanorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));

        if (request != null) {
            if (request.getName() != null) {
                if (request.getName().isBlank()) {
                    throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
                }
                hotspot.setName(request.getName().trim());
            }
            if (request.getTargetPanoramaId() != null) {
                Panorama targetPanorama = panoramaRepository
                        .findByIdAndBoothId(request.getTargetPanoramaId(), booth.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
                hotspot.setTargetPanorama(targetPanorama);
            }
            if (request.getXPosition() != null) {
                hotspot.setXPosition(request.getXPosition());
            }
            if (request.getYPosition() != null) {
                hotspot.setYPosition(request.getYPosition());
            }
            if (request.getZPosition() != null) {
                hotspot.setZPosition(request.getZPosition());
            }
        }

        return boothMapper.toHotspotResponseDTO(hotspotRepository.save(hotspot));
    }

    @Transactional
    public HotspotResponseDTO deleteHotspot(User currentUser, UUID boothId, UUID panoramaId, UUID hotspotId) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = getEditableTemplate(boothId);
        Panorama sourcePanorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
        Hotspot hotspot = hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, sourcePanorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));

        HotspotResponseDTO response = boothMapper.toHotspotResponseDTO(hotspot);
        hotspotRepository.delete(hotspot);
        return response;
    }

    private Booth getEditableTemplate(UUID boothId) {
        Booth booth = boothRepository.findTemplateByIdForUpdate(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        return booth;
    }
}
