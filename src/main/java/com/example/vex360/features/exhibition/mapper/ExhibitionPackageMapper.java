package com.example.vex360.features.exhibition.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;

@Mapper(componentModel = "spring")
public interface ExhibitionPackageMapper {

    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "templateName", source = "packageNameSnapshot")
    @Mapping(target = "templateDescription", source = "packageDescriptionSnapshot")
    @Mapping(target = "price", source = "priceSnapshot")
    @Mapping(target = "currency", source = "currencySnapshot")
    @Mapping(target = "maxProductsPerBooth", source = "maxProductsPerBoothSnapshot")
    @Mapping(target = "maxEmbeddedVideosPerBooth", source = "maxEmbeddedVideosPerBoothSnapshot")
    @Mapping(target = "maxPanoramasPerBooth", source = "maxPanoramasPerBoothSnapshot")
    @Mapping(target = "maxHotspotsPerBooth", source = "maxHotspotsPerBoothSnapshot")
    @Mapping(target = "listingPriority", source = "listingPrioritySnapshot")
    ExhibitionPackageResponseDTO toPackageResponse(ExhibitionPackage pkg);

    List<ExhibitionPackageResponseDTO> toPackageResponseList(List<ExhibitionPackage> packages);
}
