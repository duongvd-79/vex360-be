package com.example.vex360.features.exhibition.mapper;

import java.util.List;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;

@Mapper(componentModel = "spring", uses = { ExhibitionPackageMapper.class, ExhibitionAssetMapper.class })
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
    @Mapping(target = "organizationName", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "reviewedByName", source = "exhibition.reviewedBy.fullName")
    @Mapping(target = "packages", source = "packages")
    @Mapping(target = "keyVisualUrl", source = "exhibition", qualifiedByName = "getKeyVisualUrl")
    @Mapping(target = "trailerVideoUrl", source = "exhibition", qualifiedByName = "getTrailerVideoUrl")
    @Mapping(target = "floorPlanUrl", source = "exhibition", qualifiedByName = "getFloorPlanUrl")
    @Mapping(target = "guidelineUrl", source = "exhibition", qualifiedByName = "getGuidelineUrl")
    @Mapping(target = "sponsorLogos", source = "exhibition", qualifiedByName = "getSponsorLogos")
    @Mapping(target = "boothReviewDeadline", expression = "java(exhibition.getStartDate() == null ? null : exhibition.getStartDate().minusDays(3))")
    @Mapping(target = "boothPreparationOpen", expression = "java(exhibition.getStartDate() == null ? false : !java.time.LocalDate.now().isAfter(exhibition.getStartDate().minusDays(3)))")
    @Mapping(target = "daysUntilBoothDeadline", expression = "java(exhibition.getStartDate() == null ? 0L : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), exhibition.getStartDate().minusDays(3)))")
    @Mapping(target = "readinessStatus", ignore = true)
    @Mapping(target = "readinessBlockerCount", ignore = true)
    ExhibitionResponseDTO toResponse(Exhibition exhibition, List<ExhibitionPackage> packages);

    @Mapping(target = "organizerName", source = "organizer.fullName")
    @Mapping(target = "organizationName", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "reviewedByName", source = "reviewedBy.fullName")
    @Mapping(target = "packages", ignore = true)
    @Mapping(target = "keyVisualUrl", source = "exhibition", qualifiedByName = "getKeyVisualUrl")
    @Mapping(target = "trailerVideoUrl", source = "exhibition", qualifiedByName = "getTrailerVideoUrl")
    @Mapping(target = "floorPlanUrl", source = "exhibition", qualifiedByName = "getFloorPlanUrl")
    @Mapping(target = "guidelineUrl", source = "exhibition", qualifiedByName = "getGuidelineUrl")
    @Mapping(target = "sponsorLogos", source = "exhibition", qualifiedByName = "getSponsorLogos")
    @Mapping(target = "boothReviewDeadline", expression = "java(exhibition.getStartDate() == null ? null : exhibition.getStartDate().minusDays(3))")
    @Mapping(target = "boothPreparationOpen", expression = "java(exhibition.getStartDate() == null ? false : !java.time.LocalDate.now().isAfter(exhibition.getStartDate().minusDays(3)))")
    @Mapping(target = "daysUntilBoothDeadline", expression = "java(exhibition.getStartDate() == null ? 0L : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), exhibition.getStartDate().minusDays(3)))")
    @Mapping(target = "readinessStatus", ignore = true)
    @Mapping(target = "readinessBlockerCount", ignore = true)
    ExhibitionResponseDTO toResponse(Exhibition exhibition);

    @InheritConfiguration(name = "toResponse")
    @Mapping(target = "id", ignore = true)
    ExhibitionResponseDTO toPublicResponse(Exhibition exhibition, List<ExhibitionPackage> packages);

    ExhibitionPackageResponseDTO toPackageResponse(ExhibitionPackage pkg);
}
