package com.example.vex360.features.booth.services.impl;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VisitorBoothServiceImpl implements VisitorBoothService {

    private final ExhibitionService exhibitionService;
    private final BoothRepository boothRepository;
    private final BoothMapper boothMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BoothResponseDTO> getPublishedBooths(UUID exhibitionUuid, String keyword, Pageable pageable) {
        ExhibitionResponseDTO exhibition = getActiveExhibition(exhibitionUuid);

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<BoothResponseDTO> booths = boothRepository.findPublishedBoothsByExhibitionUuid(
                exhibition.getUuid(),
                BoothStatus.PUBLISHED,
                normalizedKeyword,
                pageable
        ).map(boothMapper::toBoothResponseDTO);

        return PageResponse.from(booths);
    }

    @Override
    @Transactional(readOnly = true)
    public BoothResponseDTO getBoothTourDetail(UUID exhibitionUuid, UUID boothId) {
        ExhibitionResponseDTO exhibition = getActiveExhibition(exhibitionUuid);

        BoothResponseDTO boothTour = boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                exhibition.getUuid(),
                boothId,
                BoothStatus.PUBLISHED
        ).map(boothMapper::toBoothResponseDTO)
         .orElseThrow(() -> new AppException(ErrorCode.BOOTH_NOT_FOUND));

        return boothTour;
    }

    private ExhibitionResponseDTO getActiveExhibition(UUID exhibitionUuid) {
        ExhibitionResponseDTO exhibition = exhibitionService.getExhibitionByUuid(exhibitionUuid);

        if (!ExhibitionStatus.ACTIVE.name().equals(exhibition.getStatus())) {
            throw new AppException(ErrorCode.EXHIBITION_INVALID_STATUS);
        }
        return exhibition;
    }
}
