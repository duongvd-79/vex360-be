package com.example.vex360.features.packagetemplate.mapper;

import org.springframework.stereotype.Component;

import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;

@Component
public class PackageTemplateMapper {
    public PackageTemplateResponseDTO toResponse(PackageTemplate template) {
        User createdBy = template.getCreatedBy();
        return new PackageTemplateResponseDTO(
                template.getId(),
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getEmail(),
                template.getName(),
                template.getDescription(),
                template.getPrice(),
                template.getCurrency(),
                template.getMaxProductsPerBooth(),
                template.getMaxEmbeddedVideosPerBooth(),
                template.getMaxPanoramasPerBooth(),
                template.getMaxHotspotsPerBooth(),
                template.getStorageLimitMb(),
                template.getListingPriority(),
                template.getStatus(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
