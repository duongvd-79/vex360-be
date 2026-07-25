package com.example.vex360.features.exhibition.mapper;

import java.util.Collections;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.example.vex360.features.exhibition.dtos.response.SponsorMediaResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.shared.enums.ExhibitionAssetType;

@Mapper(componentModel = "spring")
public interface ExhibitionAssetMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "url", source = "assetUrl")
    SponsorMediaResponseDTO toSponsorMediaResponse(ExhibitionAsset asset);

    List<SponsorMediaResponseDTO> toSponsorMediaResponseList(List<ExhibitionAsset> assets);

    @Named("getKeyVisualUrl")
    default String getKeyVisualUrl(Exhibition exhibition) {
        return getAssetUrl(exhibition, ExhibitionAssetType.KEY_VISUAL);
    }

    @Named("getTrailerVideoUrl")
    default String getTrailerVideoUrl(Exhibition exhibition) {
        return getAssetUrl(exhibition, ExhibitionAssetType.TRAILER_VIDEO);
    }

    @Named("getFloorPlanUrl")
    default String getFloorPlanUrl(Exhibition exhibition) {
        return getAssetUrl(exhibition, ExhibitionAssetType.FLOOR_PLAN);
    }

    @Named("getGuidelineUrl")
    default String getGuidelineUrl(Exhibition exhibition) {
        return getAssetUrl(exhibition, ExhibitionAssetType.GUIDELINE);
    }

    @Named("getSponsorLogos")
    default List<SponsorMediaResponseDTO> getSponsorLogos(Exhibition exhibition) {
        if (exhibition == null || exhibition.getAssets() == null) {
            return Collections.emptyList();
        }
        return exhibition.getAssets().stream()
                .filter(asset -> asset.getType() == ExhibitionAssetType.SPONSOR_LOGO)
                .map(this::toSponsorMediaResponse)
                .toList();
    }

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
}
