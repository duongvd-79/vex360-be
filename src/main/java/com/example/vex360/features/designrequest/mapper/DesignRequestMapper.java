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

import com.example.vex360.features.designrequest.dtos.response.DesignDraftMediaAssetResponseDTO;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;

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
    @Mapping(target = "requiredProductCount", expression = "java(countProducts(request, true))")
    @Mapping(target = "optionalProductCount", expression = "java(countProducts(request, false))")
    @Mapping(target = "reviewNote", expression = "java(latestRejectionReason(request))")
    @Mapping(target = "remainingDesignActions", ignore = true)
    DesignRequestResponseDTO toResponse(DesignRequest request);

    @Mapping(target = "mediaAssetCount", expression = "java(mediaAssetCount(draft))")
    @Mapping(target = "mediaAssetTotalBytes", expression = "java(mediaAssetTotalBytes(draft))")
    DesignDraftResponseDTO toDraftResponse(DesignDraft draft);

    @Mapping(target = "draftId", source = "draft.id")
    @Mapping(target = "assetId", source = "asset.id")
    @Mapping(target = "url", source = "asset.url")
    @Mapping(target = "fileName", source = "asset.fileName")
    @Mapping(target = "mimeType", source = "asset.mimeType")
    @Mapping(target = "fileSize", source = "asset.fileSize")
    @Mapping(target = "assetType", source = "asset.assetType")
    DesignDraftMediaAssetResponseDTO toMediaAssetResponse(DesignDraftMediaAsset mediaAsset);

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

    default String latestRejectionReason(DesignRequest request) {
        if (request == null || request.getDrafts() == null || request.getDrafts().isEmpty()) {
            return null;
        }
        return request.getDrafts().stream()
                .filter(draft -> draft.getVersionNumber() != null && draft.getVersionNumber() > 0)
                .max(Comparator.comparing(DesignDraft::getVersionNumber))
                .map(DesignDraft::getRejectionReason)
                .orElse(null);
    }

    default int countProducts(DesignRequest request, boolean required) {
        if (request.getProducts() == null) {
            return 0;
        }
        return (int) request.getProducts().stream()
                .filter(product -> Boolean.TRUE.equals(product.getRequiredFromBaseline()) == required)
                .count();
    }

    default int mediaAssetCount(DesignDraft draft) {
        return draft.getMediaAssets() == null ? 0 : draft.getMediaAssets().size();
    }

    default long mediaAssetTotalBytes(DesignDraft draft) {
        if (draft.getMediaAssets() == null) {
            return 0;
        }
        return draft.getMediaAssets().stream()
                .filter(media -> media.getAsset() != null && media.getAsset().getFileSize() != null)
                .mapToLong(media -> media.getAsset().getFileSize())
                .sum();
    }
}
