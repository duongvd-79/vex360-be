package com.example.vex360.features.designrequest.mapper;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.example.vex360.features.designrequest.dtos.response.DesignDraftResponseDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignRequestResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignRequest;

/**
 * Maps design-request aggregates to API response DTOs without applying business
 * rules or mutating entities.
 */
@Mapper(componentModel = "spring")
public interface DesignRequestMapper {

    /**
     * Maps a design request and its latest draft summary to an API response.
     *
     * @param request design request aggregate
     * @return response representation of the request
     */
    @Mapping(target = "boothId", source = "booth.id")
    @Mapping(target = "boothName", source = "booth.name")
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "exhibitionId", source = "booth.exhibitorRegistration.exhibitionPackage.exhibition.uuid")
    @Mapping(target = "exhibitionName", source = "booth.exhibitorRegistration.exhibitionPackage.exhibition.name")
    @Mapping(target = "exhibitionStartDate", source = "booth.exhibitorRegistration.exhibitionPackage.exhibition.startDate")
    @Mapping(target = "assignedDesignerId", source = "assignedDesigner.id")
    @Mapping(target = "assignedDesignerName", source = "assignedDesigner.fullName")
    @Mapping(target = "latestDraft", source = "drafts", qualifiedByName = "latestDraft")
    DesignRequestResponseDTO toResponse(DesignRequest request);

    DesignDraftResponseDTO toDraftResponse(DesignDraft draft);

    @Named("latestDraft")
    default DesignDraftResponseDTO toLatestDraft(List<DesignDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return null;
        }
        return drafts.stream()
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .map(this::toDraftResponse)
                .orElse(null);
    }
}
