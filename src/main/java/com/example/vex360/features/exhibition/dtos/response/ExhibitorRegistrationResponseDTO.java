package com.example.vex360.features.exhibition.dtos.response;

import java.time.LocalDateTime;
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
public class ExhibitorRegistrationResponseDTO {
    private Integer id;
    private UUID uuid;
    private Integer exhibitionPackageId;
    private UUID companyUserId;
    private String status;
    private LocalDateTime submittedAt;
    private String checkoutUrl;
    private String paymentStatus;
    private Long orderCode;
}
