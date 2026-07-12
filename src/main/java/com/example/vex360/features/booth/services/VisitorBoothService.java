package com.example.vex360.features.booth.services;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;

public interface VisitorBoothService {
    PageResponse<BoothResponseDTO> getPublishedBooths(UUID exhibitionUuid, String keyword, Pageable pageable);
    
    BoothResponseDTO getBoothTourDetail(UUID exhibitionUuid, UUID boothId);
}
