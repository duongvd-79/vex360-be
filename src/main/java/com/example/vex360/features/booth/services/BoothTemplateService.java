package com.example.vex360.features.booth.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.CreateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.CreatePanoramaRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.PanoramaStorageService.StoredPanoramaFile;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothTemplateService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final PanoramaStorageService panoramaStorageService;
    private final BoothMapper boothMapper;

    @Transactional
    public BoothTemplateResponseDTO createBoothTemplate(
            User currentUser,
            CreateBoothTemplateRequest request,
            Map<String, MultipartFile> files) {
        Map<String, MultipartFile> panoramaFiles = files == null ? Map.of() : files;
        validateCreateRequest(currentUser, request, panoramaFiles);
        markDefaultPanorama(request);

        Booth booth = Booth.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(request.getStatus() == null ? BoothStatus.DRAFT : request.getStatus())
                .isTemplate(true)
                .createdBy(currentUser)
                .build();

        Booth savedBooth = boothRepository.save(booth);
        List<Panorama> savedPanoramas = createPanoramas(savedBooth, request.getPanoramas(), panoramaFiles);
        savedBooth.setPanoramas(savedPanoramas);

        Map<String, Panorama> panoramaByClientKey = mapPanoramasByClientKey(request.getPanoramas(), savedPanoramas);
        List<Hotspot> savedHotspots = createHotspots(request.getPanoramas(), panoramaByClientKey);
        attachHotspotsToSourcePanoramas(savedPanoramas, savedHotspots);

        return boothMapper.toTemplateResponseDTO(savedBooth);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoothTemplateSummaryResponseDTO> getBoothTemplates(
            String keyword,
            BoothStatus status,
            Pageable pageable) {
        Page<BoothTemplateSummaryResponseDTO> templates = boothRepository
                .searchTemplates(normalizeKeyword(keyword), status, pageable)
                .map(boothMapper::toTemplateSummaryResponseDTO);
        return PageResponse.from(templates);
    }

    @Transactional(readOnly = true)
    public BoothTemplateResponseDTO getBoothTemplateById(UUID id) {
        Booth booth = boothRepository.findTemplateById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        return boothMapper.toTemplateResponseDTO(booth);
    }

    private List<Panorama> createPanoramas(
            Booth booth,
            List<CreatePanoramaRequest> panoramaRequests,
            Map<String, MultipartFile> panoramaFiles) {
        List<Panorama> panoramas = new ArrayList<>();

        for (int i = 0; i < panoramaRequests.size(); i++) {
            CreatePanoramaRequest panoramaRequest = panoramaRequests.get(i);
            StoredPanoramaFile storedFile = panoramaStorageService.store(panoramaFiles.get(panoramaRequest.getFileKey()));

            panoramas.add(Panorama.builder()
                    .booth(booth)
                    .name(panoramaRequest.getName().trim())
                    .imageUrl(storedFile.imageUrl())
                    .imageKey(storedFile.imageKey())
                    .orderIndex(panoramaRequest.getOrderIndex() == null ? i : panoramaRequest.getOrderIndex())
                    .isDefault(Boolean.TRUE.equals(panoramaRequest.getIsDefault()))
                    .build());
        }

        return panoramaRepository.saveAll(panoramas);
    }

    private Map<String, Panorama> mapPanoramasByClientKey(
            List<CreatePanoramaRequest> panoramaRequests,
            List<Panorama> panoramas) {
        Map<String, Panorama> panoramaByClientKey = new HashMap<>();
        for (int i = 0; i < panoramaRequests.size(); i++) {
            panoramaByClientKey.put(panoramaRequests.get(i).getClientKey(), panoramas.get(i));
        }
        return panoramaByClientKey;
    }

    private List<Hotspot> createHotspots(
            List<CreatePanoramaRequest> panoramaRequests,
            Map<String, Panorama> panoramaByClientKey) {
        List<Hotspot> hotspots = new ArrayList<>();

        for (CreatePanoramaRequest panoramaRequest : panoramaRequests) {
            Panorama sourcePanorama = panoramaByClientKey.get(panoramaRequest.getClientKey());
            for (CreateHotspotRequest hotspotRequest : safeHotspots(panoramaRequest)) {
                Panorama targetPanorama = panoramaByClientKey.get(hotspotRequest.getTargetPanoramaKey());

                hotspots.add(Hotspot.builder()
                        .name(hotspotRequest.getName().trim())
                        .sourcePanorama(sourcePanorama)
                        .targetPanorama(targetPanorama)
                        .xPosition(hotspotRequest.getXPosition())
                        .yPosition(hotspotRequest.getYPosition())
                        .zPosition(hotspotRequest.getZPosition())
                        .build());
            }
        }

        return hotspotRepository.saveAll(hotspots);
    }

    private void attachHotspotsToSourcePanoramas(List<Panorama> panoramas, List<Hotspot> hotspots) {
        Map<Panorama, List<Hotspot>> hotspotsBySource = hotspots.stream()
                .collect(Collectors.groupingBy(Hotspot::getSourcePanorama));

        panoramas.forEach(panorama -> panorama.setHotspots(
                new ArrayList<>(hotspotsBySource.getOrDefault(panorama, List.of()))));
    }

    private void validateCreateRequest(
            User currentUser,
            CreateBoothTemplateRequest request,
            Map<String, MultipartFile> files) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (request == null || isBlank(request.getName())) {
            throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
        }
        if (request.getPanoramas() == null || request.getPanoramas().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
        }

        validatePanoramas(request.getPanoramas(), files);
        validateHotspots(request.getPanoramas());
        validateReachablePanoramas(request.getPanoramas());
    }

    private void validatePanoramas(List<CreatePanoramaRequest> panoramas, Map<String, MultipartFile> files) {
        Set<String> clientKeys = new HashSet<>();
        Set<String> fileKeys = new HashSet<>();
        int defaultCount = 0;

        for (CreatePanoramaRequest panorama : panoramas) {
            if (panorama == null
                    || isBlank(panorama.getClientKey())
                    || isBlank(panorama.getFileKey())
                    || isBlank(panorama.getName())) {
                throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
            }

            if (!clientKeys.add(panorama.getClientKey()) || !fileKeys.add(panorama.getFileKey())) {
                throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
            }
            if (Boolean.TRUE.equals(panorama.getIsDefault())) {
                defaultCount++;
            }
        }

        if (defaultCount > 1) {
            throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
        }
        if (!files.keySet().equals(fileKeys)) {
            throw new AppException(ErrorCode.PANORAMA_FILE_INVALID);
        }
    }

    private void validateHotspots(List<CreatePanoramaRequest> panoramas) {
        Set<String> panoramaKeys = panoramas.stream()
                .map(CreatePanoramaRequest::getClientKey)
                .collect(Collectors.toSet());
        int hotspotCount = 0;

        for (CreatePanoramaRequest panorama : panoramas) {
            for (CreateHotspotRequest hotspot : safeHotspots(panorama)) {
                hotspotCount++;
                if (hotspot == null
                        || isBlank(hotspot.getName())
                        || isBlank(hotspot.getTargetPanoramaKey())
                        || hotspot.getXPosition() == null
                        || hotspot.getYPosition() == null
                        || hotspot.getZPosition() == null
                        || !panoramaKeys.contains(hotspot.getTargetPanoramaKey())) {
                    throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
                }
            }
        }

        if (panoramas.size() > 1 && hotspotCount == 0) {
            throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
        }
    }

    private void validateReachablePanoramas(List<CreatePanoramaRequest> panoramas) {
        if (panoramas.size() <= 1) {
            return;
        }

        Map<String, List<String>> targetsBySource = panoramas.stream()
                .collect(Collectors.toMap(
                        CreatePanoramaRequest::getClientKey,
                        panorama -> new ArrayList<>(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        for (CreatePanoramaRequest panorama : panoramas) {
            for (CreateHotspotRequest hotspot : safeHotspots(panorama)) {
                targetsBySource.get(panorama.getClientKey()).add(hotspot.getTargetPanoramaKey());
            }
        }

        String defaultClientKey = panoramas.stream()
                .filter(panorama -> Boolean.TRUE.equals(panorama.getIsDefault()))
                .findFirst()
                .orElse(panoramas.get(0))
                .getClientKey();

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(defaultClientKey);

        while (!queue.isEmpty()) {
            String currentKey = queue.poll();
            if (!visited.add(currentKey)) {
                continue;
            }
            targetsBySource.getOrDefault(currentKey, List.of()).forEach(queue::add);
        }

        if (visited.size() != panoramas.size()) {
            throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
        }
    }

    private void markDefaultPanorama(CreateBoothTemplateRequest request) {
        boolean hasDefaultPanorama = request.getPanoramas().stream()
                .anyMatch(panorama -> Boolean.TRUE.equals(panorama.getIsDefault()));

        if (!hasDefaultPanorama) {
            request.getPanoramas().get(0).setIsDefault(true);
        }
    }

    private List<CreateHotspotRequest> safeHotspots(CreatePanoramaRequest panorama) {
        if (panorama.getHotspots() == null) {
            return List.of();
        }
        return panorama.getHotspots();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
