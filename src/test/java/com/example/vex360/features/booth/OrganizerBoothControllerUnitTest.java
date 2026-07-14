package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.controllers.OrganizerBoothController;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothContentOverviewDTO;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

@ExtendWith(MockitoExtension.class)
class OrganizerBoothControllerUnitTest {
    @Mock
    private BoothReviewService boothReviewService;

    private OrganizerBoothController controller;
    private User organizer;
    private CustomUserDetails userDetails;
    private UUID exhibitionUuid;
    private UUID boothId;

    @BeforeEach
    void setup() {
        controller = new OrganizerBoothController(boothReviewService);
        organizer = User.builder().id(UUID.randomUUID()).build();
        userDetails = new CustomUserDetails(organizer);
        exhibitionUuid = UUID.randomUUID();
        boothId = UUID.randomUUID();
    }

    @Test
    void getContentOverview_DelegatesToService() {
        OrganizerBoothContentOverviewDTO responseDTO = new OrganizerBoothContentOverviewDTO();
        when(boothReviewService.getContentOverviewForOrganizer(organizer, exhibitionUuid, boothId)).thenReturn(responseDTO);

        ResponseEntity<ApiResponse<OrganizerBoothContentOverviewDTO>> result =
                controller.getContentOverview(userDetails, exhibitionUuid, boothId);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(responseDTO, result.getBody().data());
        verify(boothReviewService).getContentOverviewForOrganizer(organizer, exhibitionUuid, boothId);
    }

    @Test
    void getTourPreview_DelegatesToService() {
        BoothResponseDTO responseDTO = new BoothResponseDTO();
        when(boothReviewService.getTourPreviewForOrganizer(organizer, exhibitionUuid, boothId)).thenReturn(responseDTO);

        ResponseEntity<ApiResponse<BoothResponseDTO>> result =
                controller.getTourPreview(userDetails, exhibitionUuid, boothId);

        assertEquals(responseDTO, result.getBody().data());
        verify(boothReviewService).getTourPreviewForOrganizer(organizer, exhibitionUuid, boothId);
    }

    @Test
    void getReviewRequests_DelegatesToService() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "submittedAt"));
        PageResponse<BoothReviewRequestSummaryDTO> response = PageResponse.from(
                new org.springframework.data.domain.PageImpl<>(List.of(new BoothReviewRequestSummaryDTO()))
        );
        when(boothReviewService.getReviewHistoryForOrganizer(organizer, exhibitionUuid, boothId, pageable))
                .thenReturn(response);

        ResponseEntity<ApiResponse<PageResponse<BoothReviewRequestSummaryDTO>>> result =
                controller.getReviewRequests(userDetails, exhibitionUuid, boothId, pageable);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody().data());
        verify(boothReviewService).getReviewHistoryForOrganizer(organizer, exhibitionUuid, boothId, pageable);
    }

    @Test
    void getBooths_DelegatesToService() {
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<BoothResponseDTO> response = PageResponse.from(
                new org.springframework.data.domain.PageImpl<>(List.of(new BoothResponseDTO()))
        );
        when(boothReviewService.getBoothsForOrganizer(organizer, exhibitionUuid, "Booth", BoothStatus.PENDING, pageable))
                .thenReturn(response);

        ResponseEntity<ApiResponse<PageResponse<BoothResponseDTO>>> result =
                controller.getBooths(userDetails, exhibitionUuid, "Booth", BoothStatus.PENDING, pageable);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody().data());
        verify(boothReviewService).getBoothsForOrganizer(organizer, exhibitionUuid, "Booth", BoothStatus.PENDING, pageable);
    }
}
