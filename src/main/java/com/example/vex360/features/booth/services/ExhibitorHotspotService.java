package com.example.vex360.features.booth.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.dtos.request.UpsertHotspotRequest;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorHotspotService {
    private final BoothRepository boothRepository;
    private final PanoramaRepository panoramaRepository;
    private final HotspotRepository hotspotRepository;
    private final ProductService productService;
    private final MediaAssetRepository mediaAssetRepository;
    private final CompanyService companyService;
    private final BoothMapper boothMapper;
    private final BoothBenefitGuardService boothBenefitGuardService;

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
        boothBenefitGuardService.assertCanCreateHotspot(sourcePanorama.getBooth(), hotspot);
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
        boothBenefitGuardService.assertCanUpdateHotspot(sourcePanorama.getBooth(), hotspot);
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
        hotspot.setMediaClickAction(null);
        hotspot.setInfoContentType(null);
        applyCorners(hotspot, request.getType(), request.getCorners());

        switch (request.getType()) {
            case NAV -> applyNavigationHotspot(hotspot, request, booth);
            case PRODUCT -> applyProductHotspot(hotspot, request, company);
            case INFO -> applyInfoHotspot(hotspot, request, company);
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
        Product product = productService.getProductForCompany(request.getProductId(), company);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        hotspot.setProduct(product);
        hotspot.setName(resolveName(request.getName(), product.getName()));
    }

    private void applyInfoHotspot(Hotspot hotspot, UpsertHotspotRequest request, Company company) {
        HotspotInfoContentType contentType = resolveInfoContentType(request);
        hotspot.setInfoContentType(contentType);
        hotspot.setName(resolveName(request.getName(), "Info"));

        switch (contentType) {
            case NONE -> {
                hotspot.setInfoText(null);
                hotspot.setMediaAsset(null);
                hotspot.setProduct(null);
            }
            case IMAGE -> hotspot.setMediaAsset(getMediaAssetForType(request.getMediaAssetId(), company,
                    MediaAssetType.IMAGE));
            case VIDEO -> hotspot.setMediaAsset(getMediaAssetForType(request.getMediaAssetId(), company,
                    MediaAssetType.VIDEO));
            case PRODUCT -> applyProductHotspot(hotspot, request, company);
            default -> throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
    }

    private void applyMediaHotspot(Hotspot hotspot, UpsertHotspotRequest request, Company company) {
        if (request.getMediaAssetId() == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndCompanyId(request.getMediaAssetId(), company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        hotspot.setMediaAsset(mediaAsset);
        hotspot.setMediaClickAction(request.getMediaClickAction() == null
                ? HotspotMediaClickAction.DEFAULT
                : request.getMediaClickAction());
        hotspot.setName(resolveName(request.getName(), mediaAsset.getName()));
    }

    private HotspotInfoContentType resolveInfoContentType(UpsertHotspotRequest request) {
        if (request.getInfoContentType() != null) {
            return request.getInfoContentType();
        }
        if (request.getProductId() != null) {
            return HotspotInfoContentType.PRODUCT;
        }
        if (request.getMediaAssetId() != null) {
            return HotspotInfoContentType.IMAGE;
        }
        return HotspotInfoContentType.NONE;
    }

    private MediaAsset getMediaAssetForType(UUID mediaAssetId, Company company, MediaAssetType expectedType) {
        if (mediaAssetId == null) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        MediaAsset mediaAsset = mediaAssetRepository.findByIdAndCompanyId(mediaAssetId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        if (mediaAsset.getType() != expectedType) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        return mediaAsset;
    }

    private void applyCorners(Hotspot hotspot, HotspotType type, HotspotCornersDTO corners) {
        if (type != HotspotType.MEDIA && type != HotspotType.PRODUCT) {
            clearCorners(hotspot);
            return;
        }
        if (corners == null) {
            return;
        }
        if (!isCorner(corners.getTl()) || !isCorner(corners.getTr())
                || !isCorner(corners.getBl()) || !isCorner(corners.getBr())) {
            throw new AppException(ErrorCode.INVALID_HOTSPOT);
        }
        hotspot.setCornerTlX(corners.getTl().get(0));
        hotspot.setCornerTlY(corners.getTl().get(1));
        hotspot.setCornerTlZ(corners.getTl().get(2));
        hotspot.setCornerTrX(corners.getTr().get(0));
        hotspot.setCornerTrY(corners.getTr().get(1));
        hotspot.setCornerTrZ(corners.getTr().get(2));
        hotspot.setCornerBlX(corners.getBl().get(0));
        hotspot.setCornerBlY(corners.getBl().get(1));
        hotspot.setCornerBlZ(corners.getBl().get(2));
        hotspot.setCornerBrX(corners.getBr().get(0));
        hotspot.setCornerBrY(corners.getBr().get(1));
        hotspot.setCornerBrZ(corners.getBr().get(2));
    }

    private boolean isCorner(List<Double> corner) {
        return corner != null && corner.size() == 3
                && corner.get(0) != null && corner.get(1) != null && corner.get(2) != null;
    }

    private void clearCorners(Hotspot hotspot) {
        hotspot.setCornerTlX(null);
        hotspot.setCornerTlY(null);
        hotspot.setCornerTlZ(null);
        hotspot.setCornerTrX(null);
        hotspot.setCornerTrY(null);
        hotspot.setCornerTrZ(null);
        hotspot.setCornerBlX(null);
        hotspot.setCornerBlY(null);
        hotspot.setCornerBlZ(null);
        hotspot.setCornerBrX(null);
        hotspot.setCornerBrY(null);
        hotspot.setCornerBrZ(null);
    }

    private Panorama getPanoramaForCurrentUser(User currentUser, UUID boothId, UUID panoramaId) {
        Company company = getCompanyForCurrentUser(currentUser);
        Booth booth = boothRepository.findCompanyBoothById(boothId, company.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));
        return panoramaRepository.findByIdAndBoothId(panoramaId, booth.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PANORAMA_NOT_FOUND));
    }

    private Company getCompanyForCurrentUser(User currentUser) {
        return companyService.getCompanyEntityForCurrentUser(currentUser);
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
