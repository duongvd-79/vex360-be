package com.example.vex360.features.exhibition.dtos.response;

import java.util.List;

import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateSelectionResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExhibitionPackageEditContextResponseDTO {
    private List<ExhibitionPackageResponseDTO> currentPackages;
    private List<PackageTemplateSelectionResponseDTO> activeTemplates;
}
