package com.example.vex360.features.exhibition.mapper;

import java.util.Collections;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.SponsorMediaResponseDTO;
import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.entities.ExhibitionAsset;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.enums.ExhibitionAssetType;

@Mapper(componentModel = "spring")
public interface ExhibitionMapper {

    @Mapping(target = "id", source = "exhibition.id")
    @Mapping(target = "uuid", source = "exhibition.uuid")
    @Mapping(target = "name", source = "exhibition.name")
    @Mapping(target = "category", source = "exhibition.category")
    @Mapping(target = "description", source = "exhibition.description")
    @Mapping(target = "startDate", source = "exhibition.startDate")
    @Mapping(target = "endDate", source = "exhibition.endDate")
    @Mapping(target = "estimatedBooths", source = "exhibition.estimatedBooths")
    @Mapping(target = "status", source = "exhibition.status")
    @Mapping(target = "organizerName", source = "exhibition.organizer.fullName")
    @Mapping(target = "reviewedByName", source = "exhibition.reviewedBy.fullName")
    @Mapping(target = "packages", source = "packages")
    @Mapping(target = "keyVisualUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.KEY_VISUAL))")
    @Mapping(target = "trailerVideoUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.TRAILER_VIDEO))")
    @Mapping(target = "floorPlanUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.FLOOR_PLAN))")
    @Mapping(target = "guidelineUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.GUIDELINE))")
    @Mapping(target = "sponsorLogos", expression = "java(getSponsorLogos(exhibition))")
    ExhibitionResponseDTO toResponse(Exhibition exhibition, List<ExhibitionPackage> packages);

    @Mapping(target = "organizerName", source = "organizer.fullName")
    @Mapping(target = "reviewedByName", source = "reviewedBy.fullName")
    @Mapping(target = "packages", ignore = true)
    @Mapping(target = "keyVisualUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.KEY_VISUAL))")
    @Mapping(target = "trailerVideoUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.TRAILER_VIDEO))")
    @Mapping(target = "floorPlanUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.FLOOR_PLAN))")
    @Mapping(target = "guidelineUrl", expression = "java(getAssetUrl(exhibition, com.example.vex360.shared.enums.ExhibitionAssetType.GUIDELINE))")
    @Mapping(target = "sponsorLogos", expression = "java(getSponsorLogos(exhibition))")
    ExhibitionResponseDTO toResponse(Exhibition exhibition);

    default ExhibitionResponseDTO toPublicResponse(Exhibition exhibition, List<ExhibitionPackage> packages) {
        ExhibitionResponseDTO response = toResponse(exhibition, packages);
        if (response != null) {
            response.setId(null); // Hide internal ID from public response
        }
        return response;
    }

    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "templateName", source = "template.name")
    ExhibitionPackageResponseDTO toPackageResponse(ExhibitionPackage pkg);

    default String getAssetUrl(Exhibition exhibition, ExhibitionAssetType type) {
        if (exhibition == null || exhibition.getAssets() == null) {
            return null;
        }
        return exhibition.getAssets().stream()
                .filter(asset -> asset.getType() == type)
                .map(ExhibitionAsset::getAssetUrl)
                .findFirst()
                .orElse(null);
    }

    default List<SponsorMediaResponseDTO> getSponsorLogos(Exhibition exhibition) {
        if (exhibition == null || exhibition.getAssets() == null) {
            return Collections.emptyList();
        }
        return exhibition.getAssets().stream()
                .filter(asset -> asset.getType() == ExhibitionAssetType.SPONSOR_LOGO)
                .map(asset -> SponsorMediaResponseDTO.builder()
                        .id(asset.getId())
                        .url(asset.getAssetUrl())
                        .build())
                .toList();
    }
}
