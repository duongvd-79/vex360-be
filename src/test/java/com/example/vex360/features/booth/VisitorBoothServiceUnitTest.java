package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.services.impl.VisitorBoothServiceImpl;
import com.example.vex360.features.booth.services.VisitorBoothService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class VisitorBoothServiceUnitTest {

    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private BoothMapper boothMapper;

    private VisitorBoothService service;

    private UUID exhibitionUuid;
    private ExhibitionResponseDTO exhibition;
    private Pageable pageable;

    @BeforeEach
    void setup() {
        service = new VisitorBoothServiceImpl(exhibitionService, boothRepository, boothMapper);
        exhibitionUuid = UUID.randomUUID();
        exhibition = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .status(ExhibitionStatus.ACTIVE.name())
                .build();
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getPublishedBooths_WhenExhibitionIsActive_ReturnsBooths() {
        String keyword = "test";
        Booth booth = Booth.builder().id(UUID.randomUUID()).name("Test Booth").build();
        Page<Booth> boothPage = new PageImpl<>(List.of(booth));
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        responseDTO.setName("Test Booth");

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothsByExhibitionUuid(
                eq(exhibitionUuid), eq(BoothStatus.PUBLISHED), eq(keyword), eq(pageable)))
                .thenReturn(boothPage);
        when(boothMapper.toBoothResponseDTO(booth)).thenReturn(responseDTO);

        PageResponse<BoothResponseDTO> result = service.getPublishedBooths(exhibitionUuid, keyword, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Booth", result.getContent().get(0).getName());
    }

    @Test
    void getPublishedBooths_WhenExhibitionNotActive_ThrowsException() {
        exhibition.setStatus(ExhibitionStatus.REGISTRATION.name());
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);

        AppException exception = assertThrows(AppException.class, () ->
                service.getPublishedBooths(exhibitionUuid, "test", pageable));

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, exception.getErrorCode());
    }

    @Test
    void getPublishedBooths_WhenExhibitionNotFound_ThrowsException() {
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid))
                .thenThrow(new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        AppException exception = assertThrows(AppException.class, () ->
                service.getPublishedBooths(exhibitionUuid, "test", pageable));

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getBoothTourDetail_WhenExhibitionIsActiveAndBoothExists_ReturnsBoothDetail() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Test Booth").build();
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        responseDTO.setName("Test Booth");

        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                eq(exhibitionUuid), eq(boothId), eq(BoothStatus.PUBLISHED)))
                .thenReturn(Optional.of(booth));
        when(boothMapper.toBoothResponseDTO(booth)).thenReturn(responseDTO);

        BoothResponseDTO result = service.getBoothTourDetail(exhibitionUuid, boothId);

        assertNotNull(result);
        assertEquals("Test Booth", result.getName());
    }

    @Test
    void getBoothTourDetail_WhenBoothNotFound_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        when(exhibitionService.getExhibitionByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(boothRepository.findPublishedBoothByExhibitionUuidAndBoothId(
                eq(exhibitionUuid), eq(boothId), eq(BoothStatus.PUBLISHED)))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                service.getBoothTourDetail(exhibitionUuid, boothId));

        assertEquals(ErrorCode.BOOTH_NOT_FOUND, exception.getErrorCode());
    }
}
