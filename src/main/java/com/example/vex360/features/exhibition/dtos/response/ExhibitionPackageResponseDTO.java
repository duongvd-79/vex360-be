package com.example.vex360.features.exhibition.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExhibitionPackageResponseDTO {
    private Integer id;
    private UUID templateId;
    private String templateName;
    private BigDecimal finalPrice;
    private String status;
}
