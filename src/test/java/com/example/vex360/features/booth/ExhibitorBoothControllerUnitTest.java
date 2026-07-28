package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.booth.controllers.ExhibitorBoothController;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.booth.services.ExhibitorHotspotService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.ApiResponse;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothControllerUnitTest {
    private static final String AUTO_APPROVED_MESSAGE =
            "Gian hàng không có thay đổi so với phiên bản đã duyệt gần nhất. "
                    + "Hệ thống đã giữ nguyên trạng thái đã duyệt và không tạo yêu cầu xét duyệt mới.";

    @Mock
    private ExhibitorBoothService exhibitorBoothService;
    @Mock
    private ExhibitorBoothTemplateService exhibitorBoothTemplateService;
    @Mock
    private ExhibitorPanoramaService exhibitorPanoramaService;
    @Mock
    private ExhibitorHotspotService exhibitorHotspotService;
    @Mock
    private BoothReviewService boothReviewService;
    @InjectMocks
    private ExhibitorBoothController controller;

    @Test
    void submitReviewReturnsAutoApprovedMessageForReusedApprovedVersion() {
        User user = User.builder().id(UUID.randomUUID()).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UUID boothId = UUID.randomUUID();
        BoothReviewRequestSummaryDTO summary = BoothReviewRequestSummaryDTO.builder()
                .status(BoothReviewStatus.APPROVED)
                .build();
        when(boothReviewService.submitReview(user, boothId)).thenReturn(summary);

        ResponseEntity<ApiResponse<BoothReviewRequestSummaryDTO>> response =
                controller.submitReview(userDetails, boothId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(AUTO_APPROVED_MESSAGE, response.getBody().message());
        assertEquals(summary, response.getBody().data());
    }

    @Test
    void submitReviewKeepsDefaultMessageForPendingVersion() {
        User user = User.builder().id(UUID.randomUUID()).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UUID boothId = UUID.randomUUID();
        BoothReviewRequestSummaryDTO summary = BoothReviewRequestSummaryDTO.builder()
                .status(BoothReviewStatus.PENDING)
                .build();
        when(boothReviewService.submitReview(user, boothId)).thenReturn(summary);

        ResponseEntity<ApiResponse<BoothReviewRequestSummaryDTO>> response =
                controller.submitReview(userDetails, boothId);

        assertEquals(ApiResponse.success(summary).message(), response.getBody().message());
        assertEquals(summary, response.getBody().data());
    }
}
