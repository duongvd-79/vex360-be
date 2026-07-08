package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.services.impl.ExhibitionServiceImpl;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ExhibitionServiceUnitTest {

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitionPackageRepository exhibitionPackageRepository;

    @Mock
    private ExhibitionMapper exhibitionMapper;

    @InjectMocks
    private ExhibitionServiceImpl exhibitionService;

    private User organizer;
    private Exhibition registrationExhibition;
    private UUID exhibitionUuid;

    @BeforeEach
    void setUp() {
        organizer = User.builder()
                .id(UUID.randomUUID())
                .email("organizer@example.com")
                .fullName("Test Organizer")
                .build();

        exhibitionUuid = UUID.randomUUID();
        registrationExhibition = Exhibition.builder()
                .id(1)
                .uuid(exhibitionUuid)
                .name("Expo 2026")
                .status(ExhibitionStatus.REGISTRATION)
                .organizer(organizer)
                .build();
    }

    @Test
    void testPublishExhibition_Success() {
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.save(any(Exhibition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exhibitionPackageRepository.findByExhibition(any(Exhibition.class))).thenReturn(Collections.emptyList());

        ExhibitionResponseDTO responseDTO = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .status(ExhibitionStatus.PUBLISHED.name())
                .build();
        when(exhibitionMapper.toResponse(any(Exhibition.class), any())).thenReturn(responseDTO);

        ExhibitionResponseDTO result = exhibitionService.publishExhibition(organizer, exhibitionUuid);

        assertNotNull(result);
        assertEquals(ExhibitionStatus.PUBLISHED.name(), result.getStatus());
        verify(exhibitionRepository).save(registrationExhibition);
    }

    @Test
    void testPublishExhibition_UnauthorizedOrganizer_ThrowsException() {
        User anotherOrganizer = User.builder().id(UUID.randomUUID()).build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.publishExhibition(anotherOrganizer, exhibitionUuid);
        });

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void testPublishExhibition_InvalidStatus_ThrowsException() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.publishExhibition(organizer, exhibitionUuid);
        });

        assertEquals(ErrorCode.EXHIBITION_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    void testSearchExhibitionsForVisitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Exhibition publishedExhibition = Exhibition.builder()
                .id(2)
                .name("Public Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .build();
        Page<Exhibition> page = new PageImpl<>(List.of(publishedExhibition), pageable, 1);

        List<ExhibitionStatus> expectedStatuses = List.of(
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);

        when(exhibitionRepository.searchExhibitions(
                eq("Expo"), eq(expectedStatuses), eq("Tech"), any(), any(), eq(pageable)))
                .thenReturn(page);

        ExhibitionResponseDTO mockResponse = ExhibitionResponseDTO.builder()
                .id(2)
                .name("Public Expo")
                .build();
        when(exhibitionMapper.toResponse(publishedExhibition)).thenReturn(mockResponse);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForVisitor(
                "Expo", "Tech", null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertNull(result.getContent().get(0).getId()); // Should clear internal ID
    }

    @Test
    void testSearchExhibitionsForExhibitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Exhibition> page = new PageImpl<>(List.of(registrationExhibition), pageable, 1);

        List<ExhibitionStatus> expectedStatuses = List.of(
                ExhibitionStatus.REGISTRATION,
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE);

        when(exhibitionRepository.searchExhibitions(
                eq("Expo"), eq(expectedStatuses), eq("Tech"), any(), any(), eq(pageable)))
                .thenReturn(page);

        ExhibitionResponseDTO mockResponse = ExhibitionResponseDTO.builder()
                .id(1)
                .name("Expo 2026")
                .build();
        when(exhibitionMapper.toResponse(registrationExhibition)).thenReturn(mockResponse);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForExhibitor(
                "Expo", "Tech", null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getContent().get(0).getId()); // Exhibitors can see internal ID
    }

    @Test
    void testGetExhibitionByUuid_Visitor_ForbiddenStatus_ThrowsNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.REGISTRATION); // REGISTRATION is forbidden for visitors
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.getExhibitionByUuid(exhibitionUuid);
        });

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void testGetExhibitionDetailForExhibitor_Success() {
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(any(Exhibition.class))).thenReturn(Collections.emptyList());

        ExhibitionResponseDTO responseDTO = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .build();
        when(exhibitionMapper.toResponse(any(Exhibition.class), any())).thenReturn(responseDTO);

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid);

        assertNotNull(result);
        assertEquals(exhibitionUuid, result.getUuid());
    }
}
