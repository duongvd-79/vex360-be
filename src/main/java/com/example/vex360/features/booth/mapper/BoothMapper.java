package com.example.vex360.features.booth.mapper;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;

import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.dtos.response.HotspotResponseDTO;
import com.example.vex360.features.booth.dtos.response.PanoramaResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.shared.entities.User;

@Mapper(componentModel = "spring")
public interface BoothMapper {
    // Method nay giup MapStruct sinh implementation bean cho interface mapper.
    // Luong booth template van dung cac default method ben duoi de map nested data ro rang.
    String mapString(String value);

    default BoothTemplateSummaryResponseDTO toTemplateSummaryResponseDTO(Booth booth) {
        return new BoothTemplateSummaryResponseDTO(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getStatus(),
                booth.getIsTemplate(),
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
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getEmail(),
                booth.getCreatedAt(),
                booth.getUpdatedAt(),
                toPanoramaResponseDTOs(booth.getPanoramas()));
    }

    private List<PanoramaResponseDTO> toPanoramaResponseDTOs(List<Panorama> panoramas) {
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

    private PanoramaResponseDTO toPanoramaResponseDTO(Panorama panorama) {
        return new PanoramaResponseDTO(
                panorama.getId(),
                panorama.getName(),
                panorama.getImageUrl(),
                panorama.getOrderIndex(),
                panorama.getIsDefault(),
                toHotspotResponseDTOs(panorama.getHotspots()));
    }

    private List<HotspotResponseDTO> toHotspotResponseDTOs(List<Hotspot> hotspots) {
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

    private HotspotResponseDTO toHotspotResponseDTO(Hotspot hotspot) {
        Panorama source = hotspot.getSourcePanorama();
        Panorama target = hotspot.getTargetPanorama();
        return new HotspotResponseDTO(
                hotspot.getId(),
                hotspot.getName(),
                source == null ? null : source.getId(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                hotspot.getXPosition(),
                hotspot.getYPosition(),
                hotspot.getZPosition());
    }
}
