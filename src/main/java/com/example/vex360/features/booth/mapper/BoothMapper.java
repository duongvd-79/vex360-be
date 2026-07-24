package com.example.vex360.features.booth.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;

import com.example.vex360.features.booth.dtos.HotspotCornersDTO;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotPanoramaSummaryDTO;
import com.example.vex360.features.booth.dtos.response.HotspotProductSummaryDTO;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;

@Mapper(componentModel = "spring")
public interface BoothMapper {
    // Method nay giup MapStruct sinh implementation bean cho interface mapper.
    // Luong booth template van dung cac default method ben duoi de map nested data
    // ro rang.
    String mapString(String value);

    default BoothTemplateSummaryResponseDTO toTemplateSummaryResponseDTO(Booth booth) {
        return new BoothTemplateSummaryResponseDTO(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getStatus(),
                booth.getIsTemplate(),
                booth.getThumbnailUrl(),
                booth.getPanoramas() == null ? 0 : booth.getPanoramas().size(),
                booth.getCreatedAt(),
                booth.getUpdatedAt());
    }

    default BoothTemplateResponseDTO toTemplateResponseDTO(Booth booth) {
        User createdBy = booth.getCreatedBy();
        return new BoothTemplateResponseDTO(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getStatus(),
                booth.getIsTemplate(),
                booth.getThumbnailUrl(),
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getEmail(),
                booth.getCreatedAt(),
                booth.getUpdatedAt(),
                toPanoramaResponseDTOs(booth.getPanoramas()));
    }

    default BoothResponseDTO toBoothResponseDTO(Booth booth) {
        return toBoothResponseDTO(booth, booth.getPanoramas());
    }

    default BoothResponseDTO toBoothResponseDTO(Booth booth, List<Panorama> panoramas) {
        Company company = booth.getCompany();
        ExhibitorRegistration registration = booth.getExhibitorRegistration();
        User createdBy = booth.getCreatedBy();
        return new BoothResponseDTO(
                booth.getId(),
                company == null ? null : company.getId(),
                createdBy == null ? null : createdBy.getId(),
                registration == null ? null : registration.getUuid(),
                getExhibitionUuid(registration),
                getExhibitionName(registration),
                booth.getName(),
                booth.getDescription(),
                booth.getThumbnailUrl(),
                booth.getBackgroundMusicUrl(),
                booth.getBackgroundMusicFileName(),
                booth.getBackgroundMusicFileSize(),
                booth.getDisplayTemplateKey(),
                resolveListingPriority(registration),
                booth.getStatus(),
                booth.getCreatedAt(),
                booth.getUpdatedAt(),
                company == null ? null : company.getName(),
                company == null ? null : company.getIndustry(),
                company == null ? null : company.getEmail(),
                toPanoramaResponseDTOs(panoramas));
    }

    private BoothListingPriority resolveListingPriority(ExhibitorRegistration registration) {
        if (registration == null) {
            return BoothListingPriority.NORMAL;
        }
        if (registration.getListingPrioritySnapshot() != null) {
            return registration.getListingPrioritySnapshot();
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        if (exhibitionPackage != null
                && exhibitionPackage.getTemplate() != null
                && exhibitionPackage.getTemplate().getListingPriority() != null) {
            return exhibitionPackage.getTemplate().getListingPriority();
        }
        return BoothListingPriority.NORMAL;
    }

    private UUID getExhibitionUuid(ExhibitorRegistration registration) {
        Exhibition exhibition = getExhibition(registration);
        return exhibition == null ? null : exhibition.getUuid();
    }

    private String getExhibitionName(ExhibitorRegistration registration) {
        Exhibition exhibition = getExhibition(registration);
        return exhibition == null ? null : exhibition.getName();
    }

    private Exhibition getExhibition(ExhibitorRegistration registration) {
        if (registration == null) {
            return null;
        }
        ExhibitionPackage exhibitionPackage = registration.getExhibitionPackage();
        return exhibitionPackage == null ? null : exhibitionPackage.getExhibition();
    }

    default List<PanoramaResponseDTO> toPanoramaResponseDTOs(List<Panorama> panoramas) {
        if (panoramas == null) {
            return List.of();
        }
        return panoramas.stream()
                .sorted(Comparator.comparing(
                        Panorama::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::toPanoramaResponseDTO)
                .toList();
    }

    default PanoramaResponseDTO toPanoramaResponseDTO(Panorama panorama) {
        return new PanoramaResponseDTO(
                panorama.getId(),
                panorama.getName(),
                panorama.getImageUrl(),
                panorama.getImageKey(),
                panorama.getOrderIndex(),
                panorama.getIsDefault(),
                toHotspotResponseDTOs(panorama.getHotspots()));
    }

    default List<HotspotResponseDTO> toHotspotResponseDTOs(List<Hotspot> hotspots) {
        if (hotspots == null) {
            return List.of();
        }

        return hotspots.stream()
                .sorted(Comparator.comparing(
                        Hotspot::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toHotspotResponseDTO)
                .toList();
    }

    default HotspotResponseDTO toHotspotResponseDTO(Hotspot hotspot) {
        Panorama source = hotspot.getSourcePanorama();
        Panorama target = hotspot.getTargetPanorama();
        return new HotspotResponseDTO(
                hotspot.getId(),
                hotspot.getType(),
                hotspot.getName(),
                source == null ? null : source.getId(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                toPanoramaSummary(target),
                toProductSummary(hotspot.getProduct()),
                toMediaAssetResponseDTO(hotspot.getMediaAsset()),
                hotspot.getInfoText(),
                hotspot.getXPosition(),
                hotspot.getYPosition(),
                hotspot.getZPosition(),
                hotspot.getIconStyle(),
                hotspot.getScale(),
                hotspot.getZIndex(),
                hotspot.getMediaClickAction(),
                hotspot.getInfoContentType(),
                toHotspotCornersDTO(hotspot));
    }

    default HotspotCornersDTO toHotspotCornersDTO(Hotspot hotspot) {
        if (hotspot.getCornerTlX() == null || hotspot.getCornerTlY() == null || hotspot.getCornerTlZ() == null
                || hotspot.getCornerTrX() == null || hotspot.getCornerTrY() == null || hotspot.getCornerTrZ() == null
                || hotspot.getCornerBlX() == null || hotspot.getCornerBlY() == null || hotspot.getCornerBlZ() == null
                || hotspot.getCornerBrX() == null || hotspot.getCornerBrY() == null || hotspot.getCornerBrZ() == null) {
            return null;
        }
        return new HotspotCornersDTO(
                List.of(hotspot.getCornerTlX(), hotspot.getCornerTlY(), hotspot.getCornerTlZ()),
                List.of(hotspot.getCornerTrX(), hotspot.getCornerTrY(), hotspot.getCornerTrZ()),
                List.of(hotspot.getCornerBlX(), hotspot.getCornerBlY(), hotspot.getCornerBlZ()),
                List.of(hotspot.getCornerBrX(), hotspot.getCornerBrY(), hotspot.getCornerBrZ()));
    }

    default MediaAssetResponseDTO toMediaAssetResponseDTO(MediaAsset mediaAsset) {
        if (mediaAsset == null) {
            return null;
        }
        Company company = mediaAsset.getCompany();
        return new MediaAssetResponseDTO(
                mediaAsset.getId(),
                company == null ? null : company.getId(),
                mediaAsset.getName(),
                mediaAsset.getType(),
                mediaAsset.getUrl(),
                mediaAsset.getPublicId(),
                mediaAsset.getMimeType(),
                mediaAsset.getFileSize(),
                mediaAsset.getCreatedAt());
    }

    private HotspotPanoramaSummaryDTO toPanoramaSummary(Panorama panorama) {
        if (panorama == null) {
            return null;
        }
        return new HotspotPanoramaSummaryDTO(panorama.getId(), panorama.getName());
    }

    private HotspotProductSummaryDTO toProductSummary(Product product) {
        if (product == null) {
            return null;
        }
        return new HotspotProductSummaryDTO(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getThumbnailUrl(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus(),
                null,
                null);
    }
}
