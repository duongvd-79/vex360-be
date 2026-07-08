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
import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateHotspotRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
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
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoothTemplateService {
    private static final String IMAGE_RESOURCE_TYPE = "image";
    private static final java.util.Set<String> ALLOWED_THUMBNAIL_TYPES = java.util.Set.of("image/jpeg", "image/png");

    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CloudService cloudService;
    private final BoothMapper boothMapper;

    @Transactional
    public BoothTemplateResponseDTO createBoothTemplate(
            User currentUser,
            CreateBoothTemplateRequest request,
            Map<String, MultipartFile> files) {
        return createBoothTemplate(currentUser, request, null, files);
    }

    @Transactional
    public BoothTemplateResponseDTO createBoothTemplate(
            User currentUser,
            CreateBoothTemplateRequest request,
            MultipartFile thumbnail,
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

        List<String> uploadedImageKeys = new ArrayList<>();
        try {
            if (thumbnail != null && !thumbnail.isEmpty()) {
                validateThumbnail(thumbnail);
                CloudinaryResponse upload = cloudService.upload(thumbnail);
                uploadedImageKeys.add(upload.getPublicId());
                booth.setThumbnailUrl(upload.getUrl());
                booth.setThumbnailPublicId(upload.getPublicId());
            }

            Booth savedBooth = boothRepository.save(booth);
            List<Panorama> savedPanoramas = createPanoramas(
                    savedBooth,
                    request.getPanoramas(),
                    panoramaFiles,
                    uploadedImageKeys);
            savedBooth.setPanoramas(savedPanoramas);

            Map<String, Panorama> panoramaByClientKey = mapPanoramasByClientKey(request.getPanoramas(), savedPanoramas);
            List<Hotspot> savedHotspots = createHotspots(request.getPanoramas(), panoramaByClientKey);
            attachHotspotsToSourcePanoramas(savedPanoramas, savedHotspots);

            return boothMapper.toTemplateResponseDTO(savedBooth);
        } catch (RuntimeException exception) {
            cleanupUploadedImages(uploadedImageKeys);
            throw exception;
        }
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

    @Transactional
    public BoothTemplateResponseDTO deleteBoothTemplate(UUID id) {
        Booth booth = boothRepository.findTemplateById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        if (booth.getStatus() != BoothStatus.DRAFT) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

        BoothTemplateResponseDTO response = boothMapper.toTemplateResponseDTO(booth);
        List<String> imageKeys = collectOwnedImageKeys(booth);

        boothRepository.delete(booth);
        cleanupUploadedImages(imageKeys);
        return response;
    }

    private List<Panorama> createPanoramas(
            Booth booth,
            List<CreatePanoramaRequest> panoramaRequests,
            Map<String, MultipartFile> panoramaFiles,
            List<String> uploadedImageKeys) {
        List<Panorama> panoramas = new ArrayList<>();

        for (int i = 0; i < panoramaRequests.size(); i++) {
            CreatePanoramaRequest panoramaRequest = panoramaRequests.get(i);
            CloudinaryResponse uploaded = cloudService.uploadToFolder(
                    panoramaFiles.get(panoramaRequest.getFileKey()),
                    FileUploadUtils.PANORAMA_FOLDER);
            uploadedImageKeys.add(uploaded.getPublicId());

            panoramas.add(Panorama.builder()
                    .booth(booth)
                    .name(panoramaRequest.getName().trim())
                    .imageUrl(uploaded.getUrl())
                    .imageKey(uploaded.getPublicId())
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
                        .type(HotspotType.NAV)
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
        validateCreateStatus(request.getStatus());
        if (request.getPanoramas() == null || request.getPanoramas().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
        }

        validatePanoramas(request.getPanoramas(), files);
        validateHotspots(request.getPanoramas());
        validateReachablePanoramas(request.getPanoramas());
    }

    private void validateCreateStatus(BoothStatus status) {
        if (status == null || status == BoothStatus.DRAFT || status == BoothStatus.PUBLISHED) {
            return;
        }
        throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
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

    private List<String> collectOwnedImageKeys(Booth booth) {
        List<String> imageKeys = new ArrayList<>();
        imageKeys.add(booth.getThumbnailPublicId());
        booth.getPanoramas().forEach(panorama -> imageKeys.add(panorama.getImageKey()));
        return imageKeys;
    }

    private void cleanupUploadedImages(List<String> uploadedImageKeys) {
        for (String imageKey : uploadedImageKeys) {
            if (isBlank(imageKey)) {
                continue;
            }
            try {
                cloudService.delete(imageKey, IMAGE_RESOURCE_TYPE);
            } catch (RuntimeException ignored) {
                // Preserve the original create failure if cleanup also fails.
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public BoothTemplateResponseDTO updateBoothTemplate(
            User currentUser,
            UUID id,
            com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateRequest request,
            MultipartFile thumbnail) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        BoothStatus currentStatus = booth.getStatus();

        if (currentStatus == BoothStatus.PUBLISHED) {
            if (request == null) {
                throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
            }
            if (thumbnail != null && !thumbnail.isEmpty()) {
                throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
            }
            if (request.getName() != null || request.getDescription() != null) {
                throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
            }
            if (request.getStatus() != null) {
                if (request.getStatus() != BoothStatus.ARCHIVED) {
                    throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
                }
                booth.setStatus(BoothStatus.ARCHIVED);
            }
        } else if (currentStatus == BoothStatus.ARCHIVED) {
            if (request != null) {
                if (request.getName() != null) {
                    if (request.getName().isBlank()) {
                        throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
                    }
                    booth.setName(request.getName().trim());
                }
                if (request.getDescription() != null) {
                    booth.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
                }
                if (request.getStatus() != null) {
                    if (request.getStatus() != BoothStatus.PUBLISHED && request.getStatus() != BoothStatus.ARCHIVED) {
                        throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
                    }
                    booth.setStatus(request.getStatus());
                }
            }
            if (thumbnail != null && !thumbnail.isEmpty()) {
                validateThumbnail(thumbnail);
                CloudinaryResponse upload = cloudService.upload(thumbnail);
                booth.setThumbnailUrl(upload.getUrl());
                booth.setThumbnailPublicId(upload.getPublicId());
            }
        } else if (currentStatus == BoothStatus.DRAFT) {
            if (request != null) {
                if (request.getName() != null) {
                    if (request.getName().isBlank()) {
                        throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
                    }
                    booth.setName(request.getName().trim());
                }
                if (request.getDescription() != null) {
                    booth.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
                }
                if (request.getStatus() != null) {
                    if (request.getStatus() != BoothStatus.PUBLISHED && request.getStatus() != BoothStatus.DRAFT) {
                        throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
                    }
                    booth.setStatus(request.getStatus());
                }
            }
            if (thumbnail != null && !thumbnail.isEmpty()) {
                replaceThumbnail(booth, thumbnail);
            }
        }

        return boothMapper.toTemplateResponseDTO(boothRepository.save(booth));
    }

    // --- Admin Panorama Service Logic ---

    @Transactional
    public PanoramaResponseDTO createPanorama(
            User currentUser,
            UUID boothId,
            CreateExhibitorPanoramaRequest request,
            MultipartFile image) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
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
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        
        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }
        
        Panorama panorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

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
            CloudinaryResponse uploaded = cloudService.uploadToFolder(image, FileUploadUtils.PANORAMA_FOLDER);
            if (booth.getStatus() == BoothStatus.DRAFT) {
                cloudService.delete(panorama.getImageKey(), IMAGE_RESOURCE_TYPE);
            }
            panorama.setImageUrl(uploaded.getUrl());
            panorama.setImageKey(uploaded.getPublicId());
        }

        return boothMapper.toPanoramaResponseDTO(panoramaRepository.save(panorama));
    }

    @Transactional
    public PanoramaResponseDTO deletePanorama(User currentUser, UUID boothId, UUID panoramaId) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        
        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

        Panorama panorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

        if (hotspotRepository.existsByTargetPanoramaId(panoramaId)) {
            throw new AppException(ErrorCode.INVALID_PANORAMA_HOTSPOT);
        }

        PanoramaResponseDTO response = boothMapper.toPanoramaResponseDTO(panorama);
        panoramaRepository.delete(panorama);
        
        if (booth.getStatus() == BoothStatus.DRAFT) {
            cloudService.delete(panorama.getImageKey(), IMAGE_RESOURCE_TYPE);
        }
        
        return response;
    }

    // --- Admin Hotspot Service Logic ---

    @Transactional
    public HotspotResponseDTO createHotspot(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            CreateBoothTemplateHotspotRequest request) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

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
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

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
                Panorama targetPanorama = panoramaRepository.findByIdAndBoothId(request.getTargetPanoramaId(), booth.getId())
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
        Booth booth = boothRepository.findTemplateById(boothId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));

        if (booth.getStatus() == BoothStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BOOTH_NOT_EDITABLE);
        }

        Panorama sourcePanorama = panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));

        Hotspot hotspot = hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, sourcePanorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));

        HotspotResponseDTO response = boothMapper.toHotspotResponseDTO(hotspot);
        hotspotRepository.delete(hotspot);
        return response;
    }

    private int nextOrderIndex(UUID boothId) {
        return panoramaRepository.findByBoothIdOrderByOrderIndexAsc(boothId).size();
    }

    private void replaceThumbnail(Booth booth, MultipartFile thumbnail) {
        validateThumbnail(thumbnail);
        CloudinaryResponse upload = cloudService.upload(thumbnail);
        if (hasText(booth.getThumbnailPublicId())) {
            cloudService.delete(booth.getThumbnailPublicId(), IMAGE_RESOURCE_TYPE);
        }
        booth.setThumbnailUrl(upload.getUrl());
        booth.setThumbnailPublicId(upload.getPublicId());
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()
                || !ALLOWED_THUMBNAIL_TYPES.contains(normalizeMimeType(thumbnail))) {
            throw new AppException(ErrorCode.INVALID_BOOTH_TEMPLATE);
        }
    }

    private String normalizeMimeType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
