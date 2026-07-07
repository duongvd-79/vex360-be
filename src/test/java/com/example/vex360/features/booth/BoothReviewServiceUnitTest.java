package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestDetailDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothReviewServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    private BoothReviewService boothReviewService;
    private User exhibitorUser;
    private User organizer;
    private Company company;
    private Booth booth;
    private UUID exhibitionUuid;

    @BeforeEach
    void setup() {
        boothReviewService = new BoothReviewService(
                boothRepository,
                boothReviewRequestRepository,
                companyService,
                Mappers.getMapper(BoothMapper.class),
                boothReviewPolicyService);
        exhibitorUser = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        organizer = User.builder().id(UUID.randomUUID()).email("organizer@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitorUser).name("VEX Company").build();
        exhibitionUuid = UUID.randomUUID();
        booth = booth(BoothStatus.DRAFT);
    }

    @Test
    void submitReviewCreatesPendingRequestAndMarksBoothPending() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(boothReviewRequestRepository.save(org.mockito.ArgumentMatchers.any(BoothReviewRequest.class)))
                .thenAnswer(invocation -> {
                    BoothReviewRequest request = invocation.getArgument(0);
                    request.setId(UUID.randomUUID());
                    return request;
                });
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.submitReview(exhibitorUser, booth.getId());

        assertNotNull(response.getRequest().getId());
        assertEquals(BoothReviewStatus.PENDING, response.getRequest().getStatus());
        assertEquals(BoothStatus.PENDING, booth.getStatus());
    }

    @Test
    void approvePendingRequestPublishesBooth() {
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.PENDING);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);
        when(boothReviewRequestRepository.save(request)).thenReturn(request);
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.approve(organizer, exhibitionUuid, request.getId());

        assertEquals(BoothReviewStatus.APPROVED, response.getRequest().getStatus());
        assertEquals(BoothStatus.PUBLISHED, response.getBooth().getStatus());
    }

    @Test
    void rejectPendingRequestStoresReasonAndReturnsBoothToDraft() {
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.PENDING);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);
        when(boothReviewRequestRepository.save(request)).thenReturn(request);
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.reject(
                organizer,
                exhibitionUuid,
                request.getId(),
                new RejectBoothReviewRequest("Missing default panorama"));

        assertEquals(BoothReviewStatus.REJECTED, response.getRequest().getStatus());
        assertEquals("Missing default panorama", response.getRequest().getRejectedReason());
        assertEquals(BoothStatus.DRAFT, response.getBooth().getStatus());
    }

    @Test
    void approveRejectedRequestThrowsInvalidStatus() {
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.REJECTED);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> boothReviewService.approve(organizer, exhibitionUuid, request.getId()));

        assertSame(ErrorCode.INVALID_BOOTH_REVIEW_STATUS, exception.getErrorCode());
    }

    private BoothReviewRequest reviewRequest(BoothReviewStatus status) {
        return BoothReviewRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(status)
                .submittedBy(exhibitorUser)
                .build();
    }

    private Booth booth(BoothStatus status) {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .uuid(exhibitionUuid)
                .name("Expo")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(12))
                .organizer(organizer)
                .build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .id(1)
                .exhibition(exhibition)
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .exhibitionPackage(exhibitionPackage)
                .build();
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth")
                .company(company)
                .status(status)
                .isTemplate(false)
                .exhibitorRegistration(registration)
                .build();
    }
}
