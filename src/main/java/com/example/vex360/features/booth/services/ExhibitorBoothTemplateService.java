package com.example.vex360.features.booth.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothContentCountProjection;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorBoothTemplateService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final CompanyService companyService;
    private final BoothReviewPolicyService boothReviewPolicyService;
    private final BoothMapper boothMapper;

    @Transactional(readOnly = true)
    public PageResponse<ExhibitorBoothTemplateSummaryResponseDTO> getPublishedTemplates(
            String keyword,
            Pageable pageable) {
        Page<Booth> templates = boothRepository.searchTemplates(
                normalizeKeyword(keyword),
                BoothStatus.PUBLISHED,
                pageable);

        List<UUID> templateIds = templates.getContent().stream().map(Booth::getId).toList();
        Map<UUID, Long> panoramaCounts = getCounts(
                templateIds.isEmpty() ? List.of() : panoramaRepository.countByBoothIds(templateIds));
        Map<UUID, Long> hotspotCounts = getCounts(
                templateIds.isEmpty() ? List.of() : hotspotRepository.countByBoothIds(templateIds));

        return PageResponse.from(templates.map(template -> new ExhibitorBoothTemplateSummaryResponseDTO(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getThumbnailUrl(),
                panoramaCounts.getOrDefault(template.getId(), 0L),
                hotspotCounts.getOrDefault(template.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public ExhibitorBoothTemplateResponseDTO getPublishedTemplate(UUID templateId) {
        Booth template = findPublishedTemplate(templateId);
        List<Panorama> panoramas = panoramaRepository.findDetailsByBoothId(template.getId());
        validateTemplate(template, panoramas);
        return toTemplateResponse(template, panoramas);
    }

    @Transactional
    public BoothResponseDTO applyTemplate(User currentUser, UUID boothId, UUID templateId) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothByIdForUpdate(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        boothReviewPolicyService.assertEditable(booth);
        if (panoramaRepository.countByBoothId(booth.getId()) > 0) {
            throw new AppException(ErrorCode.BOOTH_TEMPLATE_REQUIRES_EMPTY_BOOTH);
        }

        Booth template = boothRepository.findTemplateByIdForUpdate(templateId)
                .filter(candidate -> candidate.getStatus() == BoothStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
        List<Panorama> templatePanoramas = panoramaRepository.findDetailsByBoothId(template.getId());
        validateTemplate(template, templatePanoramas);
        assertCompatible(getTemplateLimits(booth), templatePanoramas);

        List<Panorama> appliedPanoramas = copyPanoramas(booth, templatePanoramas);
        copyHotspots(templatePanoramas, appliedPanoramas);
        booth.getPanoramas().addAll(appliedPanoramas);
        return boothMapper.toBoothResponseDTO(booth);
    }

    private List<Panorama> copyPanoramas(Booth booth, List<Panorama> templatePanoramas) {
        List<Panorama> copies = templatePanoramas.stream()
                .map(source -> Panorama.builder()
                        .booth(booth)
                        .name(source.getName())
                        .imageUrl(source.getImageUrl())
                        .imageKey(source.getImageKey())
                        .fileSize(source.getFileSize())
                        .orderIndex(source.getOrderIndex())
                        .isDefault(source.getIsDefault())
                        .isTemplateDerived(true)
                        .build())
                .toList();
        return panoramaRepository.saveAll(copies);
    }

    private void copyHotspots(List<Panorama> sources, List<Panorama> copies) {
        Map<UUID, Panorama> copiesBySourceId = new HashMap<>();
        for (int index = 0; index < sources.size(); index++) {
            copiesBySourceId.put(sources.get(index).getId(), copies.get(index));
        }

        List<Hotspot> hotspotCopies = new ArrayList<>();
        for (Panorama sourcePanorama : sources) {
            Panorama sourceCopy = copiesBySourceId.get(sourcePanorama.getId());
            for (Hotspot sourceHotspot : sourcePanorama.getHotspots()) {
                Hotspot copy = copyNavigationHotspot(
                        sourceHotspot,
                        sourceCopy,
                        copiesBySourceId.get(sourceHotspot.getTargetPanorama().getId()));
                hotspotCopies.add(copy);
                sourceCopy.getHotspots().add(copy);
            }
        }
        hotspotRepository.saveAll(hotspotCopies);
    }

    private Hotspot copyNavigationHotspot(Hotspot source, Panorama sourcePanorama, Panorama targetPanorama) {
        Hotspot copy = Hotspot.builder()
                .type(HotspotType.NAV)
                .name(source.getName())
                .sourcePanorama(sourcePanorama)
                .targetPanorama(targetPanorama)
                .xPosition(source.getXPosition())
                .yPosition(source.getYPosition())
                .zPosition(source.getZPosition())
                .iconStyle(source.getIconStyle())
                .scale(source.getScale())
                .zIndex(source.getZIndex())
                .build();
        copy.setCornerTlX(source.getCornerTlX());
        copy.setCornerTlY(source.getCornerTlY());
        copy.setCornerTlZ(source.getCornerTlZ());
        copy.setCornerTrX(source.getCornerTrX());
        copy.setCornerTrY(source.getCornerTrY());
        copy.setCornerTrZ(source.getCornerTrZ());
        copy.setCornerBlX(source.getCornerBlX());
        copy.setCornerBlY(source.getCornerBlY());
        copy.setCornerBlZ(source.getCornerBlZ());
        copy.setCornerBrX(source.getCornerBrX());
        copy.setCornerBrY(source.getCornerBrY());
        copy.setCornerBrZ(source.getCornerBrZ());
        return copy;
    }

    private ExhibitorBoothTemplateResponseDTO toTemplateResponse(Booth template, List<Panorama> panoramas) {
        return new ExhibitorBoothTemplateResponseDTO(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getThumbnailUrl(),
                (long) panoramas.size(),
                countHotspots(panoramas),
                boothMapper.toPanoramaResponseDTOs(panoramas));
    }

    private Booth findPublishedTemplate(UUID templateId) {
        return boothRepository.findTemplateById(templateId)
                .filter(template -> template.getStatus() == BoothStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND));
    }

    private TemplateLimits getTemplateLimits(Booth booth) {
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        if (registration == null
                || registration.getMaxPanoramasPerBoothSnapshot() == null
                || registration.getMaxHotspotsPerBoothSnapshot() == null
                || registration.getMaxPanoramasPerBoothSnapshot() < 0
                || registration.getMaxHotspotsPerBoothSnapshot() < 0) {
            throw new AppException(ErrorCode.REGISTRATION_BENEFIT_LIMITS_INVALID);
        }
        return new TemplateLimits(
                registration.getMaxPanoramasPerBoothSnapshot(),
                registration.getMaxHotspotsPerBoothSnapshot());
    }

    private void validateTemplate(Booth template, List<Panorama> panoramas) {
        if (panoramas == null || panoramas.isEmpty()) {
            throw new AppException(ErrorCode.BOOTH_TEMPLATE_PANORAMA_REQUIRED);
        }
        long defaultCount = panoramas.stream().filter(p -> Boolean.TRUE.equals(p.getIsDefault())).count();
        if (defaultCount != 1) {
            throw new AppException(ErrorCode.BOOTH_TEMPLATE_DEFAULT_PANORAMA_INVALID);
        }
        for (Panorama panorama : panoramas) {
            if (panorama.getImageUrl() == null || panorama.getImageUrl().isBlank()
                    || panorama.getImageKey() == null || panorama.getImageKey().isBlank()) {
                throw new AppException(ErrorCode.BOOTH_TEMPLATE_PANORAMA_INVALID);
            }
            for (Hotspot hotspot : panorama.getHotspots()) {
                if (hotspot.getType() != HotspotType.NAV
                        || hotspot.getTargetPanorama() == null
                        || hotspot.getTargetPanorama().getBooth() == null
                        || !template.getId().equals(hotspot.getTargetPanorama().getBooth().getId())) {
                    throw new AppException(ErrorCode.BOOTH_TEMPLATE_HOTSPOT_INVALID);
                }
            }
        }
    }

    private void assertCompatible(TemplateLimits limits, List<Panorama> panoramas) {
        if (panoramas.size() > limits.maxPanoramas()) {
            throw new AppException(ErrorCode.BOOTH_PANORAMA_LIMIT_EXCEEDED);
        }
        if (countHotspots(panoramas) > limits.maxHotspots()) {
            throw new AppException(ErrorCode.BOOTH_HOTSPOT_LIMIT_EXCEEDED);
        }
    }

    private long countHotspots(List<Panorama> panoramas) {
        return panoramas.stream().mapToLong(p -> p.getHotspots().size()).sum();
    }

    private Map<UUID, Long> getCounts(List<BoothContentCountProjection> projections) {
        return projections.stream().collect(Collectors.toMap(
                BoothContentCountProjection::getBoothId,
                BoothContentCountProjection::getContentCount));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private record TemplateLimits(long maxPanoramas, long maxHotspots) {
    }
}
