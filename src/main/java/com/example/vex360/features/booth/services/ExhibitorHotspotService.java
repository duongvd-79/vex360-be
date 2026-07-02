package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.shared.entities.Company;
import com.example.vex360.shared.entities.Product;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorHotspotService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final ProductRepository productRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CompanyRepository companyRepository;
    private final BoothMapper boothMapper;

    @Transactional(readOnly = true)
    public List<HotspotResponseDTO> getHotspots(User currentUser, UUID boothId, UUID panoramaId) {
        Panorama panorama = getPanoramaForCurrentUser(currentUser, boothId, panoramaId);
        return boothMapper.toHotspotResponseDTOs(
                hotspotRepository.findBySourcePanoramaIdOrderByNameAsc(panorama.getId()));
    }

    @Transactional
    public HotspotResponseDTO createHotspot(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            UpsertHotspotRequest request) {
        Panorama sourcePanorama = getPanoramaForCurrentUser(currentUser, boothId, panoramaId);
        Company company = sourcePanorama.getBooth().getCompany();
        Hotspot hotspot = Hotspot.builder()
                .sourcePanorama(sourcePanorama)
                .build();
        applyRequest(hotspot, request, sourcePanorama.getBooth(), company);
        return boothMapper.toHotspotResponseDTO(hotspotRepository.save(hotspot));
    }

    @Transactional
    public HotspotResponseDTO updateHotspot(
            User currentUser,
            UUID boothId,
            UUID panoramaId,
            UUID hotspotId,
            UpsertHotspotRequest request) {
        Panorama sourcePanorama = getPanoramaForCurrentUser(currentUser, boothId, panoramaId);
        Hotspot hotspot = hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, sourcePanorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));
        applyRequest(hotspot, request, sourcePanorama.getBooth(), sourcePanorama.getBooth().getCompany());
        return boothMapper.toHotspotResponseDTO(hotspotRepository.save(hotspot));
    }

    @Transactional
    public HotspotResponseDTO deleteHotspot(User currentUser, UUID boothId, UUID panoramaId, UUID hotspotId) {
        Panorama sourcePanorama = getPanoramaForCurrentUser(currentUser, boothId, panoramaId);
        Hotspot hotspot = hotspotRepository.findByIdAndSourcePanoramaId(hotspotId, sourcePanorama.getId())
                .orElseThrow(() -> new AppException(ErrorCode.HOTSPOT_NOT_FOUND));
        HotspotResponseDTO response = boothMapper.toHotspotResponseDTO(hotspot);
        hotspotRepository.delete(hotspot);
        return response;
    }

    private void applyRequest(Hotspot hotspot, UpsertHotspotRequest request, Booth booth, Company company) {
        if (request == null || request.getType() == null
                || request.getXPosition() == null
                || request.getYPosition() == null
                || request.getZPosition() == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }

        hotspot.setType(request.getType());
        hotspot.setXPosition(request.getXPosition());
        hotspot.setYPosition(request.getYPosition());
        hotspot.setZPosition(request.getZPosition());
        hotspot.setIconStyle(trimToNull(request.getIconStyle()));
        hotspot.setScale(request.getScale());
        hotspot.setZIndex(request.getZIndex());
        hotspot.setTargetPanorama(null);
        hotspot.setProduct(null);
        hotspot.setMediaAsset(null);
        hotspot.setInfoText(null);

        switch (request.getType()) {
            case NAV -> applyNavigationHotspot(hotspot, request, booth);
            case PRODUCT -> applyProductHotspot(hotspot, request, company);
            case INFO -> applyInfoHotspot(hotspot, request);
            case MEDIA -> applyMediaHotspot(hotspot, request, company);
            default -> throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
    }

    private void applyNavigationHotspot(Hotspot hotspot, UpsertHotspotRequest request, Booth booth) {
        if (request.getTargetPanoramaId() == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        Panorama target = panoramaRepository.findByIdAndBoothId(request.getTargetPanoramaId(), booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
        hotspot.setTargetPanorama(target);
        hotspot.setName(resolveName(request.getName(), target.getName()));
    }

    private void applyProductHotspot(Hotspot hotspot, UpsertHotspotRequest request, Company company) {
        if (request.getProductId() == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        Product product = productRepository.findByIdAndCompanyId(request.getProductId(), company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(request.getName(), product.getName()));
    }

    private void applyInfoHotspot(Hotspot hotspot, UpsertHotspotRequest request) {
        String infoText = trimToNull(request.getInfoText());
        if (infoText == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        hotspot.setInfoText(infoText);
        hotspot.setName(resolveName(request.getName(), "Info"));
    }

    private void applyMediaHotspot(Hotspot hotspot, UpsertHotspotRequest request, Company company) {
        if (request.getMediaAssetId() == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndCompanyId(request.getMediaAssetId(), company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        hotspot.setMediaAsset(mediaAsset);
        hotspot.setName(resolveName(request.getName(), mediaAsset.getName()));
    }

    private Panorama getPanoramaForCurrentUser(User currentUser, UUID boothId, UUID panoramaId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        return panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return companyRepository.findByOwnerUserId(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.COMPANY_NOT_FOUND));
    }

    private String resolveName(String requestedName, String fallbackName) {
        String name = trimToNull(requestedName);
        if (name != null) {
            return name;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName.trim();
        }
        throw new AppException(ErrorCode.INVALID_HOTSPOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
