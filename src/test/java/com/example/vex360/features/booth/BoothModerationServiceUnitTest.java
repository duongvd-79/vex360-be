package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.booth.dtos.request.BanBoothRequest;
import com.example.vex360.features.booth.dtos.request.WarnBoothRequest;
import com.example.vex360.features.booth.dtos.response.BoothModerationSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.services.AdminBoothModerationService;
import com.example.vex360.features.booth.services.BoothReviewContentAssembler;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothModerationServiceUnitTest {

    @Mock
    private BoothRepository boothRepository;
    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;
    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;
    @Mock
    private BoothReviewContentAssembler contentAssembler;
    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private MailService mailService;
    @Mock
    private BoothMapper boothMapper;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    private Clock clock;
    private AdminBoothModerationService moderationService;

    private User admin;
    private User exhibitor;
    private Company company;
    private Booth booth;
    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC); // Aug 10
        moderationService = new AdminBoothModerationService(
                boothRepository,
                boothReviewRequestRepository,
                boothReviewPolicyService,
                contentAssembler,
                exhibitionService,
                new ExhibitionParticipationPolicy(),
                mailService,
                boothMapper,
                afterCommitExecutor,
                clock);

        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).email("admin@test.com").fullName("Admin System")
                .build();
        exhibitor = User.builder().id(UUID.randomUUID()).role(Role.EXHIBITOR).email("exhibitor@test.com")
                .fullName("Nguyen Van A").build();
        company = Company.builder().id(UUID.randomUUID()).name("Công ty A").ownerUser(exhibitor).build();

        // Start date Aug 20 (T-10 days away on Aug 10)
        exhibition = Exhibition.builder()
                .id(1)
                .experienceMode(com.example.vex360.shared.enums.ExhibitionExperienceMode.WITH_BOOTHS)
                .uuid(UUID.randomUUID())
                .name("Exhibition 2026")
                .startDate(LocalDate.of(2026, Month.AUGUST, 20))
                .endDate(LocalDate.of(2026, Month.AUGUST, 25))
                .status(ExhibitionStatus.PUBLISHED)
                .build();

        ExhibitionPackage pkg = ExhibitionPackage.builder().id(1).exhibition(exhibition).build();
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(1).exhibitionPackage(pkg).company(company)
                .build();

        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth 1")
                .company(company)
                .createdBy(exhibitor)
                .exhibitorRegistration(reg)
                .status(BoothStatus.PUBLISHED)
                .isTemplate(false)
                .warningCount(0)
                .build();

        org.mockito.Mockito.lenient()
                .when(boothRepository.save(any(Booth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void warnBooth_Success_WhenBeforeT3() {
        UUID exhibitionUuid = exhibition.getUuid();
        UUID boothId = booth.getId();

        when(boothRepository.findDetailForAdmin(boothId, exhibitionUuid)).thenReturn(Optional.of(booth));

        WarnBoothRequest request = WarnBoothRequest.builder()
                .warningReason("Nội dung không phù hợp chuẩn mực")
                .build();

        moderationService.warnBooth(admin, exhibitionUuid, boothId, request);

        assertEquals(BoothStatus.DRAFT, booth.getStatus());
        assertEquals(1, booth.getWarningCount());
        assertEquals("Nội dung không phù hợp chuẩn mực", booth.getWarningReason());
        assertNotNull(booth.getWarnedAt());
        assertEquals(admin, booth.getWarnedBy());
        verify(boothRepository).save(booth);
    }

    @Test
    void warnBooth_ThrowsException_WhenAlreadyWarned() {
        UUID exhibitionUuid = exhibition.getUuid();
        UUID boothId = booth.getId();
        booth.setWarningCount(1);

        when(boothRepository.findDetailForAdmin(boothId, exhibitionUuid)).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_ALREADY_WARNED))
                .when(boothReviewPolicyService).assertCanWarnBooth(booth);

        WarnBoothRequest request = WarnBoothRequest.builder()
                .warningReason("Nội dung không phù hợp lần 2")
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> moderationService.warnBooth(admin, exhibitionUuid, boothId, request));

        assertSame(ErrorCode.BOOTH_ALREADY_WARNED, ex.getErrorCode());
    }

    @Test
    void warnBooth_ThrowsException_WhenAtOrAfterT3() {
        UUID exhibitionUuid = exhibition.getUuid();
        UUID boothId = booth.getId();

        when(boothRepository.findDetailForAdmin(boothId, exhibitionUuid)).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_WARNING_NOT_ALLOWED_AFTER_DEADLINE))
                .when(boothReviewPolicyService).assertCanWarnBooth(booth);

        WarnBoothRequest request = WarnBoothRequest.builder()
                .warningReason("Cảnh báo muộn")
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> moderationService.warnBooth(admin, exhibitionUuid, boothId, request));

        assertSame(ErrorCode.BOOTH_WARNING_NOT_ALLOWED_AFTER_DEADLINE, ex.getErrorCode());
    }

    @Test
    void banBooth_Success_EvenAtOrAfterT3() {
        UUID exhibitionUuid = exhibition.getUuid();
        UUID boothId = booth.getId();

        when(boothRepository.findDetailForAdmin(boothId, exhibitionUuid)).thenReturn(Optional.of(booth));

        BanBoothRequest request = BanBoothRequest.builder()
                .banReason("Vi phạm nghiêm trọng chính sách")
                .build();

        moderationService.banBooth(admin, exhibitionUuid, boothId, request);

        assertEquals(BoothStatus.BANNED, booth.getStatus());
        assertEquals("Vi phạm nghiêm trọng chính sách", booth.getBanReason());
        assertNotNull(booth.getBannedAt());
        assertEquals(admin, booth.getBannedBy());
        verify(boothRepository).save(booth);
    }

    @Test
    void getModerationSummary_ReturnsOnlyModeratedBoothsWithLatestReason() {
        booth.setWarningCount(1);
        booth.setWarningReason("Nội dung sai quy định");
        booth.setWarnedAt(Instant.parse("2026-08-09T10:00:00Z"));
        PageRequest pageable = PageRequest.of(0, 10);
        when(boothRepository.searchModeratedForAdmin(null, pageable))
                .thenReturn(new PageImpl<>(List.of(booth), pageable, 1));

        var result = moderationService.getModerationSummary(admin, null, pageable);

        assertEquals(1, result.getTotalElements());
        BoothModerationSummaryDTO item = result.getContent().getFirst();
        assertEquals(booth.getId(), item.getBoothId());
        assertEquals(exhibition.getUuid(), item.getExhibitionId());
        assertEquals(exhibition.getName(), item.getExhibitionName());
        assertEquals("WARNING", item.getLatestAction());
        assertEquals("Nội dung sai quy định", item.getLatestReason());
    }

    @Test
    void getContentOverview_FindsBoothWithoutExhibitionFilter() {
        BoothResponseDTO boothResponse = new BoothResponseDTO();
        BoothReviewContentOverviewDTO contentOverview = new BoothReviewContentOverviewDTO();
        when(boothRepository.findDetailForAdmin(booth.getId())).thenReturn(Optional.of(booth));
        when(boothMapper.toBoothResponseDTO(booth)).thenReturn(boothResponse);
        when(contentAssembler.toContentOverview(booth)).thenReturn(contentOverview);

        var result = moderationService.getContentOverviewForAdmin(admin, booth.getId());

        assertSame(boothResponse, result.getBooth());
        assertSame(contentOverview, result.getContentOverview());
        verify(boothRepository).findDetailForAdmin(booth.getId());
    }

}
