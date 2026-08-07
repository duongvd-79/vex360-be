package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import com.example.vex360.features.mail.AfterCommitExecutor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;

import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageEditContextResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.request.AdminExhibitionStatusFilter;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.ReconcileExhibitionPackagesRequest;
import com.example.vex360.features.exhibition.dtos.request.ReconcileExhibitionPackagesRequest.PackageSelection;
import com.example.vex360.features.exhibition.dtos.request.ReconcileExhibitionPackagesRequest.SelectionType;
import com.example.vex360.features.exhibition.dtos.request.SponsorRequestDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.repositories.AdminExhibitionProjection;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.impl.ExhibitionServiceImpl;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import com.example.vex360.shared.enums.Role;
import com.example.vex360.features.exhibition.services.ExhibitionReviewHistoryService;

import com.example.vex360.features.exhibition.repositories.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class ExhibitionServiceUnitTest {

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitionPackageRepository exhibitionPackageRepository;

    @Mock
    private PackageTemplateService packageTemplateService;

    @Mock
    private ExhibitionAssetRepository exhibitionAssetRepository;

    @Mock
    private ExhibitorRegistrationRepository exhibitorRegistrationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BoothRepository boothRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private ExhibitionMapper exhibitionMapper;

    @Mock
    private CloudService cloudService;

    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;

    @Mock
    private UserService userService;

    @Mock
    private ExhibitionReviewHistoryService reviewHistoryService;

    @Mock
    private MailService mailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExhibitionServiceImpl exhibitionService;

    private User organizer;
    private Exhibition registrationExhibition;
    private UUID exhibitionUuid;

    @BeforeEach
    void setUp() {
        lenient().when(exhibitionRepository.findByUuidForUpdate(any()))
                .thenAnswer(invocation -> exhibitionRepository.findByUuid(invocation.getArgument(0)));
        lenient().when(timelinePolicy.today()).thenReturn(LocalDate.now());
        lenient().when(timelinePolicy.hasMinimumLeadTime(any())).thenReturn(true);

        exhibitionService = new ExhibitionServiceImpl(
                exhibitionRepository,
                exhibitionPackageRepository,
                packageTemplateService,
                exhibitionAssetRepository,
                exhibitorRegistrationRepository,
                paymentRepository,
                companyService,
                exhibitionMapper,
                cloudService,
                timelinePolicy,
                userService,
                reviewHistoryService,
                mailService,
                new AfterCommitExecutor(),
                eventPublisher);

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
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .build();

        ReflectionTestUtils.setField(exhibitionService, "timelinePolicy", timelinePolicy);
        org.mockito.Mockito.lenient().when(timelinePolicy.hasMinimumLeadTime(any())).thenReturn(true);
    }

    @Test
    void countPendingExhibitionsReturnsPendingCount() {
        when(exhibitionRepository.countByStatus(ExhibitionStatus.PENDING)).thenReturn(3L);

        long count = exhibitionService.countPendingExhibitions();

        assertEquals(3L, count);
        verify(exhibitionRepository).countByStatus(ExhibitionStatus.PENDING);
    }

    @Test
    void getExhibitionDetailForAdminEnrichesOrganizerCompanyContact() {
        Company company = Company.builder()
                .ownerUser(organizer)
                .name("VEX Organizer Company")
                .email("company@example.com")
                .phone("0901234567")
                .build();
        ExhibitionResponseDTO mappedResponse = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .name(registrationExhibition.getName())
                .organizerName(organizer.getFullName())
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition))
                .thenReturn(Collections.emptyList());
        when(exhibitionMapper.toResponse(registrationExhibition, Collections.emptyList()))
                .thenReturn(mappedResponse);
        when(companyService.findByOwnerUserId(organizer.getId())).thenReturn(Optional.of(company));

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid);

        assertEquals(exhibitionUuid, result.getUuid());
        assertEquals(registrationExhibition.getName(), result.getName());
        assertEquals(organizer.getFullName(), result.getOrganizerName());
        assertEquals(company.getName(), result.getOrganizationName());
        assertEquals(company.getEmail(), result.getEmail());
        assertEquals(company.getPhone(), result.getPhone());
        verify(companyService).findByOwnerUserId(organizer.getId());
    }

    @Test
    void getExhibitionDetailForAdminKeepsResponseWhenCompanyIsMissing() {
        ExhibitionResponseDTO mappedResponse = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .name(registrationExhibition.getName())
                .organizerName(organizer.getFullName())
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition))
                .thenReturn(Collections.emptyList());
        when(exhibitionMapper.toResponse(registrationExhibition, Collections.emptyList()))
                .thenReturn(mappedResponse);
        when(companyService.findByOwnerUserId(organizer.getId())).thenReturn(Optional.empty());

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid);

        assertEquals(mappedResponse, result);
        assertNull(result.getOrganizationName());
        assertNull(result.getEmail());
        assertNull(result.getPhone());
        verify(companyService).findByOwnerUserId(organizer.getId());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void testPublishExhibition_Success() {
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.save(any(Exhibition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exhibitionPackageRepository.findByExhibition(any(Exhibition.class)))
                .thenReturn(Collections.emptyList());

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
    void testPublishExhibition_StatusNotReady_ThrowsException() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.publishExhibition(organizer, exhibitionUuid);
        });

        assertEquals(ErrorCode.EXHIBITION_NOT_READY_TO_PUBLISH, ex.getErrorCode());
    }

    @Test
    void searchExhibitionsForAdminMapsApprovedAndFrontendSortAliases() {
        String companyName = "VEX Organizer Company";

        Pageable requestedPageable = PageRequest.of(
                1, 10, Sort.by(
                        Sort.Order.asc("companyName"),
                        Sort.Order.asc("organizerName"),
                        Sort.Order.desc("expectedBoothCount"),
                        Sort.Order.asc("exhibitionName")));
        Pageable mappedPageable = PageRequest.of(
                1, 10, Sort.by(
                        Sort.Order.asc("c.name"),
                        Sort.Order.asc("organizer.fullName"),
                        Sort.Order.desc("estimatedBooths"),
                        Sort.Order.asc("name")));
        List<ExhibitionStatus> approvedStatuses = List.of(
                ExhibitionStatus.REGISTRATION,
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);
        AdminExhibitionProjection row = mock(AdminExhibitionProjection.class);
        when(row.getExhibition()).thenReturn(registrationExhibition);
        when(row.getCompanyName()).thenReturn(companyName);
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(row), mappedPageable, 11);
        when(exhibitionRepository.searchAdminExhibitions(
                "Expo", approvedStatuses, "Tech", null, null, mappedPageable))
                .thenReturn(page);
        when(exhibitionMapper.toResponse(registrationExhibition))
                .thenReturn(ExhibitionResponseDTO.builder()
                        .name("Expo 2026")
                        .organizerName(organizer.getFullName())
                        .build());

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForAdmin(
                " Expo ", AdminExhibitionStatusFilter.APPROVED, " Tech ",
                null, null, requestedPageable);

        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(11, result.getTotalElements());
        assertEquals("Expo 2026", result.getContent().get(0).getName());
        assertEquals(companyName, result.getContent().get(0).getCompanyName());
        assertEquals(organizer.getFullName(), result.getContent().get(0).getOrganizerName());
        verify(exhibitionRepository).searchAdminExhibitions(
                "Expo", approvedStatuses, "Tech", null, null, mappedPageable);
    }

    @Test
    void searchExhibitionsForAdminFiltersExactStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        when(exhibitionRepository.searchAdminExhibitions(
                null, List.of(ExhibitionStatus.PENDING), null, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForAdmin(
                " ", AdminExhibitionStatusFilter.PENDING, " ", null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(exhibitionRepository).searchAdminExhibitions(
                null, List.of(ExhibitionStatus.PENDING), null, null, null, pageable);
    }

    @Test
    void searchExhibitionsForAdminUsesAllStatusesWhenFilterIsMissing() {
        Pageable pageable = PageRequest.of(0, 10);
        List<ExhibitionStatus> allStatuses = List.of(ExhibitionStatus.values());
        AdminExhibitionProjection row = mock(AdminExhibitionProjection.class);
        when(row.getExhibition()).thenReturn(registrationExhibition);
        when(row.getCompanyName()).thenReturn(organizer.getFullName());
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(row), pageable, 1);
        when(exhibitionRepository.searchAdminExhibitions(
                null, allStatuses, null, null, null, pageable))
                .thenReturn(page);
        when(exhibitionMapper.toResponse(registrationExhibition))
                .thenReturn(ExhibitionResponseDTO.builder()
                        .organizerName(organizer.getFullName())
                        .build());

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForAdmin(
                null, null, null, null, null, pageable);

        assertEquals(organizer.getFullName(), result.getContent().get(0).getCompanyName());
        verify(exhibitionRepository).searchAdminExhibitions(
                null, allStatuses, null, null, null, pageable);
    }

    @Test
    void testSearchExhibitionsForVisitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Exhibition publishedExhibition = Exhibition.builder()
                .id(2)
                .name("Public Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .build();
        AdminExhibitionProjection row = mock(AdminExhibitionProjection.class);
        when(row.getExhibition()).thenReturn(publishedExhibition);
        when(row.getCompanyName()).thenReturn("Public Co");
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(row), pageable, 1);

        List<ExhibitionStatus> expectedStatuses = List.of(
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);

        when(exhibitionRepository.searchAdminExhibitions(
                eq("Expo"), eq(expectedStatuses), eq("Tech"), any(), any(), eq(pageable)))
                .thenReturn(page);

        ExhibitionResponseDTO publicResponse = ExhibitionResponseDTO.builder()
                .name("Public Expo")
                .build();
        when(exhibitionMapper.toPublicResponse(publishedExhibition, null)).thenReturn(publicResponse);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForVisitor(
                "Expo", null, "Tech", null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertNull(result.getContent().get(0).getId()); // Should clear internal ID
        assertEquals("Public Co", result.getContent().get(0).getCompanyName());
    }

    @Test
    void searchExhibitionsForVisitorFiltersExactPublicStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(), pageable, 0);
        when(exhibitionRepository.searchAdminExhibitions(
                null, List.of(ExhibitionStatus.ACTIVE), null, null, null, pageable))
                .thenReturn(page);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForVisitor(
                " ", ExhibitionStatus.ACTIVE, " ", null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(exhibitionRepository).searchAdminExhibitions(
                null, List.of(ExhibitionStatus.ACTIVE), null, null, null, pageable);
    }

    @Test
    void searchExhibitionsForVisitorReturnsEmptyPageForNonPublicStatus() {
        Pageable pageable = PageRequest.of(2, 10);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForVisitor(
                null, ExhibitionStatus.REGISTRATION, null, null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        assertEquals(2, result.getPage());
        assertEquals(10, result.getSize());
        verify(exhibitionRepository, never()).searchAdminExhibitions(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void testSearchExhibitionsForExhibitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        AdminExhibitionProjection row = mock(AdminExhibitionProjection.class);
        when(row.getExhibition()).thenReturn(registrationExhibition);
        when(row.getCompanyName()).thenReturn("Organizer Company");
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(row), pageable, 1);

        List<ExhibitionStatus> expectedStatuses = List.of(
                ExhibitionStatus.REGISTRATION,
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE);

        when(exhibitionRepository.searchAdminExhibitions(
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
        assertEquals("Organizer Company", result.getContent().get(0).getCompanyName());
        verify(companyService, never()).findByOwnerUserId(any());
    }

    @Test
    void testGetExhibitionByUuid_Visitor_ForbiddenStatus_ThrowsNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.REGISTRATION); // REGISTRATION is forbidden for
                                                                         // visitors
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.getExhibitionByUuid(exhibitionUuid);
        });

        assertEquals(ErrorCode.EXHIBITION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void testGetExhibitionByUuid_Visitor_Success() {
        Exhibition publishedExhibition = Exhibition.builder()
                .id(2)
                .uuid(exhibitionUuid)
                .name("Public Expo")
                .status(ExhibitionStatus.PUBLISHED)
                .build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(publishedExhibition));

        ExhibitionResponseDTO publicResponse = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .name("Public Expo")
                .build();
        when(exhibitionMapper.toPublicResponse(publishedExhibition, null)).thenReturn(publicResponse);

        ExhibitionResponseDTO result = exhibitionService.getExhibitionByUuid(exhibitionUuid);

        assertNotNull(result);
        assertEquals(exhibitionUuid, result.getUuid());
        assertNull(result.getId()); // Should hide ID
        verify(exhibitionPackageRepository, never()).findByExhibition(any());
    }

    @Test
    void testGetExhibitionDetailForExhibitor_Success() {
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(any(Exhibition.class)))
                .thenReturn(Collections.emptyList());

        ExhibitionResponseDTO responseDTO = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .build();
        when(exhibitionMapper.toResponse(any(Exhibition.class), any())).thenReturn(responseDTO);

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid);

        assertNotNull(result);
        assertEquals(exhibitionUuid, result.getUuid());
    }

    @Test
    void testPublishExhibition_InvalidStatus_ThrowsException() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class, () -> {
            exhibitionService.publishExhibition(organizer, exhibitionUuid);
        });

        assertEquals(ErrorCode.EXHIBITION_NOT_READY_TO_PUBLISH, ex.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = ExhibitionStatus.class, names = { "REGISTRATION", "PUBLISHED", "ACTIVE", "COMPLETED" })
    void updateExhibitionPackage_nonDraftExhibition_throwsInvalidStatus(ExhibitionStatus status) {
        registrationExhibition.setStatus(status);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(organizer, exhibitionUuid, 10,
                        new ConfigureExhibitionPackageRequest()));

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_CHANGES_NOT_ALLOWED, exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = ExhibitionStatus.class, names = { "REGISTRATION", "PUBLISHED", "ACTIVE", "COMPLETED" })
    void deleteExhibitionPackage_nonDraftExhibition_throwsInvalidStatus(ExhibitionStatus status) {
        registrationExhibition.setStatus(status);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 10));

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_CHANGES_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void createExhibitionRequest_pastStartDate_isInvalid() {
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Past Expo")
                .category("Technology")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(1))
                .estimatedBooths(1)
                .build();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString()
                            .equals("startDate")));
        }
    }

    @Test
    void createExhibition_exceedsMaxDuration_throwsAppException() {
        MultipartFile keyVisual = imageFile();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Long Expo")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(150)) // > 90 days
                .estimatedBooths(10)
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, null));
        assertEquals(ErrorCode.EXHIBITION_DURATION_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void createExhibition_duplicateNameCaseInsensitive_throwsAppException() {
        MultipartFile keyVisual = imageFile();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("EXPO 2026")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10)
                .build();

        when(exhibitionRepository.existsByNameIgnoreCase("EXPO 2026")).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, null));
        assertEquals(ErrorCode.EXHIBITION_NAME_DUPLICATED, ex.getErrorCode());
    }

    @Test
    void uploadSponsorLogo_transactionRollback_deletesNewCloudAsset() {
        MultipartFile file = imageFile();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(cloudService.upload(file)).thenReturn(cloudResponse("new-logo"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        beginTransactionSynchronization();

        exhibitionService.uploadSponsorLogo(organizer, exhibitionUuid, "VinFast", file);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cloudService).delete("new-logo", "image");
    }

    @Test
    void createExhibition_transactionRollback_deletesUploadedKeyVisual() {
        MultipartFile keyVisual = imageFile();
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest pkgReq = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();
        PackageTemplate template = PackageTemplate.builder()
                .id(templateId)
                .price(BigDecimal.ONE)
                .listingPriority(BoothListingPriority.NORMAL)
                .status(PackageTemplateStatus.ACTIVE)
                .build();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("New Expo")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10)
                .packages(List.of(pkgReq))
                .build();
        when(packageTemplateService.getActivePackageTemplateEntity(templateId)).thenReturn(template);
        when(exhibitionRepository.save(any(Exhibition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudService.upload(keyVisual)).thenReturn(cloudResponse("new-key-visual"));
        beginTransactionSynchronization();

        exhibitionService.createExhibition(organizer, request, keyVisual, null);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cloudService).delete("new-key-visual", "image");
    }

    @Test
    void updateSponsorLogo_transactionCommit_deletesOldAssetOnlyAfterCommit() {
        MultipartFile file = imageFile();
        ExhibitionAsset asset = sponsorAsset("old-logo");
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(cloudService.upload(file)).thenReturn(cloudResponse("new-logo"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        beginTransactionSynchronization();

        exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, asset.getId(), "VinFast", file);
        verify(cloudService, never()).delete("old-logo", "image");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(cloudService).delete("old-logo", "image");
        verify(cloudService, never()).delete("new-logo", "image");
    }

    @Test
    void updateSponsorLogo_transactionRollback_keepsOldAssetAndDeletesNewAsset() {
        MultipartFile file = imageFile();
        ExhibitionAsset asset = sponsorAsset("old-logo");
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(cloudService.upload(file)).thenReturn(cloudResponse("new-logo"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        beginTransactionSynchronization();

        exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, asset.getId(), "VinFast", file);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cloudService, never()).delete("old-logo", "image");
        verify(cloudService).delete("new-logo", "image");
    }

    @Test
    void deleteSponsorLogo_transactionCommit_deletesCloudAssetOnlyAfterCommit() {
        ExhibitionAsset asset = sponsorAsset("old-logo");
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        beginTransactionSynchronization();

        exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, asset.getId());
        verify(cloudService, never()).delete("old-logo", "image");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(cloudService).delete("old-logo", "image");
    }

    private MultipartFile imageFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);
        return file;
    }

    private CloudinaryResponse cloudResponse(String publicId) {
        return CloudinaryResponse.builder()
                .url("https://cdn.example/" + publicId)
                .publicId(publicId)
                .build();
    }

    private ExhibitionAsset sponsorAsset(String publicId) {
        return ExhibitionAsset.builder()
                .id(UUID.randomUUID())
                .exhibition(registrationExhibition)
                .publicId(publicId)
                .type(ExhibitionAssetType.SPONSOR_LOGO)
                .build();
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager
                .getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }

    @Test
    void updateExhibitionForOrganizer_nonDraftStatus_throwsDetailsChangesNotAllowed() {
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req,
                        null));
        assertEquals(ErrorCode.EXHIBITION_DETAILS_CHANGES_NOT_ALLOWED, ex.getErrorCode());
        assertEquals("Chỉ có thể chỉnh sửa hồ sơ khi đang chờ duyệt hoặc đã bị từ chối.",
                ex.getErrorCode().getMessage());
    }

    @Test
    void updateExhibitionForOrganizer_maxRejectionLimit_throwsResubmissionLimitReached() {
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        registrationExhibition.setRejectionCount(3);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req,
                        null));
        assertEquals(ErrorCode.EXHIBITION_RESUBMISSION_LIMIT_REACHED, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizer_onOrAfterStartDate_throwsAlreadyStarted() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now());
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req,
                        null));
        assertEquals(ErrorCode.EXHIBITION_ALREADY_STARTED, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizer_hasRegistrations_throwsHasRegistrations() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now().plusDays(20));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository
                .existsByExhibitionPackageExhibitionId(registrationExhibition.getId()))
                .thenReturn(true);

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req,
                        null));
        assertEquals(ErrorCode.EXHIBITION_HAS_REGISTRATIONS, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizerUsesTimelinePolicyDate() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.of(2026, 1, 11));
        when(timelinePolicy.today()).thenReturn(LocalDate.of(2026, 1, 10));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository
                .existsByExhibitionPackageExhibitionId(registrationExhibition.getId()))
                .thenReturn(true);
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.of(2026, 1, 20))
                .endDate(LocalDate.of(2026, 1, 25))
                .build();

        AppException exception = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(
                        organizer, exhibitionUuid, request, null));

        assertEquals(ErrorCode.EXHIBITION_HAS_REGISTRATIONS, exception.getErrorCode());
    }

    @Test
    void updateExhibitionMedia_pendingStatus_throwsAssetChangesNotAllowed() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(organizer, exhibitionUuid, null, null,
                        null));
        assertEquals(ErrorCode.EXHIBITION_ASSET_CHANGES_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void approveExhibition_lateApprovalAllowed_resolvesTargetStatus() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now().plusDays(2));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(timelinePolicy.resolveTargetStatus(any(), any())).thenReturn(ExhibitionStatus.PUBLISHED);

        PackageTemplate template = PackageTemplate.builder()
                .status(PackageTemplateStatus.ACTIVE)
                .listingPriority(BoothListingPriority.NORMAL)
                .price(BigDecimal.TEN)
                .build();
        when(exhibitionPackageRepository.findByExhibition(any())).thenReturn(List.of(
                ExhibitionPackage.builder()
                        .template(template)
                        .priceSnapshot(BigDecimal.TEN)
                        .listingPrioritySnapshot(BoothListingPriority.NORMAL)
                        .finalPrice(BigDecimal.TEN)
                        .status(ExhibitionPackageStatus.ACTIVE)
                        .build()));

        when(exhibitionMapper.toResponse(any(), any())).thenReturn(ExhibitionResponseDTO.builder().build());

        ExhibitionResponseDTO response = exhibitionService.approveExhibition(admin, exhibitionUuid);
        assertNotNull(response);
        assertEquals(ExhibitionStatus.PUBLISHED, registrationExhibition.getStatus());
    }

    @Test
    void rejectExhibition_ThirdRejectionEmailsOriginalName() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setRejectionCount(2);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.save(any(Exhibition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition))
                .thenReturn(Collections.emptyList());

        exhibitionService.rejectExhibition(admin, exhibitionUuid,
                RejectExhibitionRequest.builder().rejectedReason("Missing documents").build());

        assertTrue(registrationExhibition.getName().startsWith("Expo 2026 (Rejected-"));
        verify(mailService).sendExhibitionReviewResultEmail(
                eq("organizer@example.com"),
                eq("Test Organizer"),
                eq("Expo 2026"),
                any(),
                any(),
                eq("REJECTED"),
                eq("Missing documents"),
                eq(3),
                any());
    }

    @Test
    void updateExhibitionPackage_packageInUse_throwsPackageInUse() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository.existsByExhibitionPackageId(10)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(organizer, exhibitionUuid, 10,
                        new ConfigureExhibitionPackageRequest()));
        assertEquals(ErrorCode.EXHIBITION_PACKAGE_IN_USE, ex.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = ExhibitionStatus.class, names = { "REGISTRATION", "PUBLISHED", "ACTIVE", "COMPLETED" })
    void uploadSponsorLogo_afterApproval_throwsSponsorChangesNotAllowed(ExhibitionStatus status) {
        registrationExhibition.setStatus(status);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        MultipartFile file = mock(MultipartFile.class);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(organizer, exhibitionUuid, "Sponsor", file));

        assertEquals(ErrorCode.EXHIBITION_SPONSOR_CHANGES_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizer_statusRejected_resubmitsSuccessfully() {
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        registrationExhibition.setRejectionCount(1);
        registrationExhibition.setRejectedReason("Invalid docs");
        registrationExhibition.setReviewedBy(User.builder().id(UUID.randomUUID()).build());
        registrationExhibition.setReviewedAt(Instant.now());

        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest pkgReq = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();
        PackageTemplate template = PackageTemplate.builder()
                .id(templateId)
                .price(BigDecimal.ONE)
                .listingPriority(BoothListingPriority.NORMAL)
                .status(PackageTemplateStatus.ACTIVE)
                .build();

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("Resubmitted Expo")
                .category("Tech")
                .description("Desc")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .estimatedBooths(50)
                .packages(List.of(pkgReq))
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(timelinePolicy.hasMinimumLeadTime(req.getStartDate())).thenReturn(true);
        when(packageTemplateService.getActivePackageTemplateEntity(templateId)).thenReturn(template);
        when(exhibitionRepository.save(any(Exhibition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(exhibitionMapper.toResponse(any(Exhibition.class), anyList()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null);

        assertEquals(ExhibitionStatus.PENDING, registrationExhibition.getStatus());
        assertNull(registrationExhibition.getRejectedReason());
        assertNull(registrationExhibition.getReviewedBy());
        assertNull(registrationExhibition.getReviewedAt());
        assertEquals(1, registrationExhibition.getRejectionCount());
    }

    @Test
    void approveExhibition_noPackages_throwsValidationFailed() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now().plusDays(20));

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.approveExhibition(admin, exhibitionUuid));

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_COUNT_INVALID, ex.getErrorCode());
    }

    @Test
    void createExhibition_inactiveTemplate_throwsPackageTemplateNotFound() {
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest pkgReq = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();
        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Expo")
                .category("Tech")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10)
                .packages(List.of(pkgReq))
                .build();

        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenThrow(new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, req, imageFile(), null));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(exhibitionRepository, never()).save(any());
    }

    @Test
    void updateExhibitionForOrganizer_inactiveTemplate_throwsPackageTemplateNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest pkgReq = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();
        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("Updated Expo")
                .category("Tech")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10)
                .packages(List.of(pkgReq))
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(timelinePolicy.hasMinimumLeadTime(req.getStartDate())).thenReturn(true);
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenThrow(new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req,
                        null));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(exhibitionRepository, never()).save(any());
    }

    @Test
    void configureExhibitionPackage_inactiveTemplate_throwsPackageTemplateNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest req = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenThrow(new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(organizer, exhibitionUuid, req));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(exhibitionPackageRepository, never()).save(any());
    }

    @Test
    void addExhibitionPackage_inactiveTemplate_throwsPackageTemplateNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest req = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenThrow(new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, req));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(exhibitionPackageRepository, never()).save(any());
    }

    @Test
    void updateExhibitionPackage_inactiveTemplate_throwsPackageTemplateNotFound() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest req = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId)
                .finalPrice(BigDecimal.TEN)
                .build();
        ExhibitionPackage existingPkg = ExhibitionPackage.builder()
                .id(10)
                .exhibition(registrationExhibition)
                .template(PackageTemplate.builder().id(UUID.randomUUID()).build())
                .build();

        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository.existsByExhibitionPackageId(10)).thenReturn(false);
        when(exhibitionPackageRepository.findById(10)).thenReturn(Optional.of(existingPkg));
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenThrow(new AppException(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(organizer, exhibitionUuid, 10, req));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, ex.getErrorCode());
        verify(exhibitionPackageRepository, never()).save(any());
    }

    @Test
    void queryAndAnalyticsDelegatesReturnRepositoryResults() {
        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now();
        List<Integer> ids = List.of(1, 2);
        List<Object[]> rows = Collections.singletonList(new Object[] { 1, 7L });
        List<Exhibition> exhibitions = List.of(registrationExhibition);

        when(exhibitionRepository.countExhibitionsByStatus()).thenReturn(rows);
        when(exhibitionRepository.aggregateDailyCreated(start, end)).thenReturn(rows);
        when(exhibitionRepository.findAll()).thenReturn(exhibitions);
        when(exhibitionRepository.count()).thenReturn(2L);
        when(paymentRepository.countAdminPaymentsByStatus(start, end)).thenReturn(rows);
        when(paymentRepository.aggregateAdminPaidMetrics(start, end)).thenReturn(rows);
        when(paymentRepository.aggregateAdminDailyRevenue(start, end)).thenReturn(rows);
        when(paymentRepository.aggregateRevenueByExhibition(ids, start, end)).thenReturn(rows);
        when(exhibitorRegistrationRepository.countByStatusGroupedByExhibition(
                ids, ExhibitorRegistrationStatus.APPROVED)).thenReturn(rows);
        when(exhibitorRegistrationRepository.aggregateDailySubmissions(ids, start, end)).thenReturn(rows);
        when(exhibitorRegistrationRepository.countByExhibitionPackageExhibitionIdAndStatus(
                1, ExhibitorRegistrationStatus.APPROVED)).thenReturn(7L);
        when(paymentRepository.aggregateOrganizerDailyRevenue(ids, start, end)).thenReturn(rows);
        when(paymentRepository.aggregateOrganizerPackageRevenue(ids, start, end)).thenReturn(rows);
        when(paymentRepository.aggregateDailyRevenue(1, start, end)).thenReturn(rows);
        when(paymentRepository.aggregatePaidPackageRevenue(1, start, end)).thenReturn(rows);
        when(exhibitionAssetRepository.existsByPublicId("asset")).thenReturn(true);

        assertEquals(rows, exhibitionService.countExhibitionsByStatus());
        assertEquals(rows, exhibitionService.aggregateDailyCreatedExhibitions(start, end));
        assertEquals(exhibitions, exhibitionService.getAllExhibitions());
        assertEquals(2L, exhibitionService.countExhibitions());
        assertEquals(rows, exhibitionService.countAdminPaymentsByStatus(start, end));
        assertEquals(rows, exhibitionService.aggregateAdminPaidMetrics(start, end));
        assertEquals(rows, exhibitionService.aggregateAdminDailyRevenue(start, end));
        assertEquals(Map.of(1, 7L), exhibitionService.aggregateRevenueByExhibition(ids, start, end));
        assertEquals(Map.of(1, 7L), exhibitionService.countRegistrationsByStatusGroupedByExhibition(
                ids, ExhibitorRegistrationStatus.APPROVED));
        assertEquals(rows, exhibitionService.aggregateDailyRegistrationSubmissions(ids, start, end));
        assertEquals(7L, exhibitionService.countApprovedRegistrationsForExhibition(1));
        assertEquals(rows, exhibitionService.aggregateOrganizerDailyRevenue(ids, start, end));
        assertEquals(rows, exhibitionService.aggregateOrganizerPackageRevenue(ids, start, end));
        assertEquals(rows, exhibitionService.aggregateDailyRevenueForExhibition(1, start, end));
        assertEquals(rows, exhibitionService.aggregatePaidPackageRevenueForExhibition(1, start, end));
        assertTrue(exhibitionService.isAssetReferenced("asset"));
    }

    @Test
    void entityLookupsCoverPresentMissingAndNullKeys() {
        when(exhibitionRepository.findById(1)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.findById(2)).thenReturn(Optional.empty());
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.findByUuid(UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(Optional.empty());
        when(exhibitionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionRepository.findByIdForUpdate(2)).thenReturn(Optional.empty());

        assertEquals(registrationExhibition, exhibitionService.getExhibitionEntityById(1));
        assertThrows(AppException.class, () -> exhibitionService.getExhibitionEntityById(2));
        assertEquals(registrationExhibition, exhibitionService.findExhibitionEntityByUuid(exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.findExhibitionEntityByUuid(
                UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertEquals(registrationExhibition, exhibitionService.findExhibitionForUpdate(1));
        assertThrows(AppException.class, () -> exhibitionService.findExhibitionForUpdate(2));
        assertThrows(AppException.class, () -> exhibitionService.findExhibitionForUpdate((Integer) null));
        assertEquals(registrationExhibition, exhibitionService.findExhibitionForUpdate(exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.findExhibitionForUpdate(
                UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertThrows(AppException.class, () -> exhibitionService.findExhibitionForUpdate((UUID) null));
    }

    @Test
    void organizerListsAndPendingCountsCoverAuthenticationAndRows() {
        User missingId = User.builder().build();
        List<Exhibition> exhibitions = List.of(registrationExhibition);
        when(exhibitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId()))
                .thenReturn(exhibitions);
        when(exhibitorRegistrationRepository.countActionRequiredGroupedByExhibition(anyList(), any()))
                .thenReturn(Collections.singletonList(new Object[] { 1, 3L }));

        assertThrows(AppException.class, () -> exhibitionService.getOrganizerExhibitions(null));
        assertThrows(AppException.class, () -> exhibitionService.getOrganizerExhibitions(missingId));
        assertEquals(exhibitions, exhibitionService.getOrganizerExhibitions(organizer));
        assertEquals(Map.of(), exhibitionService.getPendingRegistrationCountsGroupedByExhibition(null));
        assertEquals(Map.of(), exhibitionService.getPendingRegistrationCountsGroupedByExhibition(List.of()));
        assertEquals(Map.of(1, 3L),
                exhibitionService.getPendingRegistrationCountsGroupedByExhibition(List.of(1)));
    }

    @Test
    void imageValidationCoversMissingTypeSizeAndValidFiles() {
        MultipartFile empty = mock(MultipartFile.class);
        MultipartFile missingType = mock(MultipartFile.class);
        MultipartFile wrongType = mock(MultipartFile.class);
        MultipartFile tooLarge = mock(MultipartFile.class);
        MultipartFile valid = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        when(wrongType.getContentType()).thenReturn("text/plain");
        when(tooLarge.getContentType()).thenReturn("image/png");
        when(tooLarge.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        when(valid.getContentType()).thenReturn("IMAGE/JPEG");

        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", null,
                        true));
        ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", null, false);
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", empty,
                        true));
        ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", empty, false);
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile",
                        missingType, false));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile",
                        wrongType, false));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", tooLarge,
                        false));
        ReflectionTestUtils.invokeMethod(exhibitionService, "validateImageFile", valid, false);
    }

    @Test
    void videoValidationCoversMissingTypeSizeAndValidFiles() {
        MultipartFile empty = mock(MultipartFile.class);
        MultipartFile missingType = mock(MultipartFile.class);
        MultipartFile wrongType = mock(MultipartFile.class);
        MultipartFile tooLarge = mock(MultipartFile.class);
        MultipartFile valid = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        when(wrongType.getContentType()).thenReturn("video/webm");
        when(tooLarge.getContentType()).thenReturn("video/mp4");
        when(tooLarge.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        when(valid.getContentType()).thenReturn("VIDEO/MP4");

        ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile", (MultipartFile) null);
        ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile", empty);
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile",
                        missingType));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile",
                        wrongType));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile",
                        tooLarge));
        ReflectionTestUtils.invokeMethod(exhibitionService, "validateVideoFile", valid);
    }

    @Test
    void packageRequestValidationCoversEveryInvalidShapeAndSuccess() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PackageTemplate first = PackageTemplate.builder().id(firstId).price(BigDecimal.TEN)
                .listingPriority(BoothListingPriority.NORMAL).build();
        PackageTemplate duplicate = PackageTemplate.builder().id(secondId).price(BigDecimal.TEN)
                .listingPriority(BoothListingPriority.NORMAL).build();
        ConfigureExhibitionPackageRequest valid = ConfigureExhibitionPackageRequest.builder()
                .templateId(firstId).finalPrice(BigDecimal.TEN).build();
        ConfigureExhibitionPackageRequest missingTemplate = ConfigureExhibitionPackageRequest.builder()
                .finalPrice(BigDecimal.TEN).build();
        ConfigureExhibitionPackageRequest missingPrice = ConfigureExhibitionPackageRequest.builder()
                .templateId(firstId).build();
        ConfigureExhibitionPackageRequest belowFloor = ConfigureExhibitionPackageRequest.builder()
                .templateId(firstId).finalPrice(BigDecimal.ONE).build();
        ConfigureExhibitionPackageRequest duplicatePriority = ConfigureExhibitionPackageRequest.builder()
                .templateId(secondId).finalPrice(BigDecimal.TEN).build();
        when(packageTemplateService.getActivePackageTemplateEntity(firstId)).thenReturn(first);
        when(packageTemplateService.getActivePackageTemplateEntity(secondId)).thenReturn(duplicate);

        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateAndResolvePackages",
                        (Object) null));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validateAndResolvePackages",
                        List.of()));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(valid, valid, valid, valid)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", Collections.singletonList(null)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(missingTemplate)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(missingPrice)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(belowFloor)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(valid, duplicatePriority)));
        assertNotNull(ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateAndResolvePackages", List.of(valid)));
    }

    @Test
    void approvalPackageValidationCoversEveryInvalidShapeAndSuccess() {
        PackageTemplate active = PackageTemplate.builder().price(BigDecimal.TEN)
                .status(PackageTemplateStatus.ACTIVE).listingPriority(BoothListingPriority.NORMAL)
                .build();
        PackageTemplate inactive = PackageTemplate.builder().price(BigDecimal.TEN)
                .status(PackageTemplateStatus.INACTIVE).listingPriority(BoothListingPriority.PRIORITY)
                .build();
        ExhibitionPackage valid = ExhibitionPackage.builder().id(1).status(ExhibitionPackageStatus.ACTIVE)
                .template(active).priceSnapshot(BigDecimal.TEN)
                .listingPrioritySnapshot(BoothListingPriority.NORMAL)
                .finalPrice(BigDecimal.TEN).build();

        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validatePackagesForApproval",
                        (Object) null));
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(exhibitionService, "validatePackagesForApproval",
                        List.of()));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(valid, valid, valid, valid)));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.INACTIVE).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.ACTIVE).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.ACTIVE).template(inactive).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.ACTIVE).template(active).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.ACTIVE).template(active)
                        .finalPrice(BigDecimal.valueOf(-1)).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(ExhibitionPackage.builder()
                        .status(ExhibitionPackageStatus.ACTIVE).template(active)
                        .finalPrice(BigDecimal.ONE).build())));
        assertThrows(AppException.class, () -> ReflectionTestUtils.invokeMethod(exhibitionService,
                "validatePackagesForApproval", List.of(valid, valid)));
        ReflectionTestUtils.invokeMethod(exhibitionService, "validatePackagesForApproval", List.of(valid));
    }

    @Test
    void protectedOperationsRejectNullUsersAndUsersWithoutIds() {
        User missingId = User.builder().role(Role.ADMIN).build();

        assertThrows(AppException.class, () -> exhibitionService.createExhibition(null, null, null, null));
        assertThrows(AppException.class, () -> exhibitionService.createExhibition(missingId, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(null, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(missingId, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.searchExhibitionsForOrganizer(null, null, null, null, null,
                        null, null));
        assertThrows(AppException.class, () -> exhibitionService.searchExhibitionsForOrganizer(
                missingId, null, null, null, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForOrganizer(null, exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForOrganizer(missingId, exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(null, exhibitionUuid, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(missingId, exhibitionUuid, null,
                        null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(null, exhibitionUuid, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(missingId, exhibitionUuid, null, null,
                        null));
        assertThrows(AppException.class, () -> exhibitionService.approveExhibition(null, exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.approveExhibition(missingId, exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.rejectExhibition(null, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.rejectExhibition(missingId, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(null, exhibitionUuid, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(missingId, exhibitionUuid, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(null, exhibitionUuid, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(missingId, exhibitionUuid, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(null, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(missingId, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(null, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(missingId, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(null, exhibitionUuid, 1, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(missingId, exhibitionUuid, 1, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(null, exhibitionUuid, 1));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(missingId, exhibitionUuid, 1));
        assertThrows(AppException.class, () -> exhibitionService.publishExhibition(null, exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.publishExhibition(missingId, exhibitionUuid));
    }

    @Test
    void lookupsCoverNotFoundCallbacksForEveryPublicOperation() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> exhibitionService.getExhibitionByUuid(exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(organizer, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForOrganizer(organizer, exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, null,
                        null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(organizer, exhibitionUuid, null, null,
                        null));
        assertThrows(AppException.class, () -> exhibitionService.approveExhibition(admin, exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.rejectExhibition(admin, exhibitionUuid,
                        mock(RejectExhibitionRequest.class)));
        assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(organizer, exhibitionUuid, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, null, null, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(organizer, exhibitionUuid, 1, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 1));
        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid));
        assertThrows(AppException.class, () -> exhibitionService.publishExhibition(organizer, exhibitionUuid));
    }

    @Test
    void organizerSearchNormalizesFiltersAndMapsResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Exhibition> page = new PageImpl<>(List.of(registrationExhibition), pageable, 1);
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder().name("Expo 2026").build();
        when(exhibitionRepository.searchOrganizerExhibitions(
                organizer.getId(), null, null, null, null, null, pageable)).thenReturn(page);
        when(exhibitionRepository.searchOrganizerExhibitions(
                organizer.getId(), "Expo", ExhibitionStatus.PENDING, "Tech", null, null, pageable))
                .thenReturn(page);
        when(exhibitionMapper.toResponse(registrationExhibition)).thenReturn(dto);

        assertEquals(dto, exhibitionService.searchExhibitionsForOrganizer(
                organizer, null, null, null, null, null, pageable).getContent().get(0));
        assertEquals(dto, exhibitionService.searchExhibitionsForOrganizer(
                organizer, " ", null, " ", null, null, pageable).getContent().get(0));
        assertEquals(dto, exhibitionService.searchExhibitionsForOrganizer(
                organizer, " Expo ", ExhibitionStatus.PENDING, " Tech ", null, null, pageable)
                .getContent().get(0));
    }

    @Test
    void organizerDetailCoversUnauthorizedAndSuccess() {
        User another = User.builder().id(UUID.randomUUID()).build();
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder().build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of())).thenReturn(dto);

        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForOrganizer(another, exhibitionUuid));
        assertEquals(dto, exhibitionService.getExhibitionDetailForOrganizer(organizer, exhibitionUuid));
    }

    @Test
    void publicAndExhibitorDetailsCoverEveryAllowedStatusBranch() {
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder().build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionMapper.toPublicResponse(registrationExhibition, null)).thenReturn(dto);
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of())).thenReturn(dto);

        registrationExhibition.setStatus(ExhibitionStatus.ACTIVE);
        assertEquals(dto, exhibitionService.getExhibitionByUuid(exhibitionUuid));
        assertEquals(dto, exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid));
        registrationExhibition.setStatus(ExhibitionStatus.COMPLETED);
        assertEquals(dto, exhibitionService.getExhibitionByUuid(exhibitionUuid));
        assertThrows(AppException.class,
                () -> exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid));
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
        assertEquals(dto, exhibitionService.getExhibitionDetailForExhibitor(exhibitionUuid));
    }

    @Test
    void exhibitorSearchCoversNullBlankAndTrimmedFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        List<ExhibitionStatus> statuses = List.of(
                ExhibitionStatus.REGISTRATION, ExhibitionStatus.PUBLISHED, ExhibitionStatus.ACTIVE);
        AdminExhibitionProjection row = mock(AdminExhibitionProjection.class);
        when(row.getExhibition()).thenReturn(registrationExhibition);
        Page<AdminExhibitionProjection> page = new PageImpl<>(List.of(row), pageable, 1);
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder().build();
        when(exhibitionRepository.searchAdminExhibitions(null, statuses, null, null, null, pageable))
                .thenReturn(page);
        when(exhibitionRepository.searchAdminExhibitions("Expo", statuses, "Tech", null, null, pageable))
                .thenReturn(page);
        when(exhibitionMapper.toResponse(registrationExhibition)).thenReturn(dto);

        exhibitionService.searchExhibitionsForExhibitor(null, null, null, null, pageable);
        exhibitionService.searchExhibitionsForExhibitor(" ", " ", null, null, pageable);
        exhibitionService.searchExhibitionsForExhibitor(" Expo ", " Tech ", null, null, pageable);
    }

    @Test
    void configurePackageCoversAuthorizationPriceDuplicateAndSuccess() {
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest request = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId).finalPrice(BigDecimal.TEN).build();
        PackageTemplate expensive = PackageTemplate.builder().id(templateId).price(BigDecimal.valueOf(11))
                .build();
        PackageTemplate template = PackageTemplate.builder().id(templateId).price(BigDecimal.TEN).build();
        ExhibitionPackage duplicate = ExhibitionPackage.builder().build();
        ExhibitionResponseDTO ignored = ExhibitionResponseDTO.builder().build();
        User another = User.builder().id(UUID.randomUUID()).build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenReturn(expensive, template, template);
        when(exhibitionPackageRepository.findByExhibitionIdAndTemplateId(1, templateId))
                .thenReturn(Optional.of(duplicate), Optional.empty());
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionMapper.toPackageResponse(any(ExhibitionPackage.class))).thenReturn(null);

        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(another, exhibitionUuid, request));
        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(organizer, exhibitionUuid, request));
        assertThrows(AppException.class,
                () -> exhibitionService.configureExhibitionPackage(organizer, exhibitionUuid, request));
        assertNull(exhibitionService.configureExhibitionPackage(organizer, exhibitionUuid, request));
        assertNotNull(ignored);
    }

    @Test
    void updateMediaCoversRejectedUnauthorizedAndCreateOrReplaceAssets() {
        User another = User.builder().id(UUID.randomUUID()).build();
        MultipartFile video = mock(MultipartFile.class);
        MultipartFile floorPlan = imageFile();
        MultipartFile guideline = imageFile();
        when(video.getContentType()).thenReturn("video/mp4");
        when(video.getSize()).thenReturn(1024L);
        ExhibitionAsset oldTrailer = ExhibitionAsset.builder()
                .exhibition(registrationExhibition).publicId("old-video")
                .type(ExhibitionAssetType.TRAILER_VIDEO).build();
        registrationExhibition.getAssets().add(oldTrailer);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(another, exhibitionUuid, video, null,
                        null));
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(organizer, exhibitionUuid, video, null,
                        null));

        registrationExhibition.setStatus(ExhibitionStatus.ACTIVE);
        when(cloudService.upload(video)).thenReturn(cloudResponse("new-video"));
        when(cloudService.upload(floorPlan)).thenReturn(cloudResponse("floor"));
        when(cloudService.upload(guideline)).thenReturn(cloudResponse("guide"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        assertNotNull(exhibitionService.updateExhibitionMedia(
                organizer, exhibitionUuid, video, floorPlan, guideline));
        assertEquals("new-video", oldTrailer.getPublicId());
    }

    @Test
    void addPackageCoversLimitFloorPriorityAndSuccess() {
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest request = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId).finalPrice(BigDecimal.TEN).build();
        PackageTemplate expensive = PackageTemplate.builder().id(templateId).price(BigDecimal.valueOf(11))
                .listingPriority(BoothListingPriority.NORMAL).build();
        PackageTemplate normal = PackageTemplate.builder().id(templateId).price(BigDecimal.TEN)
                .listingPriority(BoothListingPriority.NORMAL).build();
        ExhibitionPackage normalPackage = ExhibitionPackage.builder()
                .template(PackageTemplate.builder().listingPriority(BoothListingPriority.NORMAL)
                        .build())
                .listingPrioritySnapshot(BoothListingPriority.NORMAL)
                .build();
        ExhibitionPackage priorityPackage = ExhibitionPackage.builder()
                .template(PackageTemplate.builder().listingPriority(BoothListingPriority.PRIORITY)
                        .build())
                .listingPrioritySnapshot(BoothListingPriority.PRIORITY)
                .build();
        List<ExhibitionPackage> threePackages = List.of(normalPackage, priorityPackage,
                ExhibitionPackage.builder().build());
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition))
                .thenReturn(threePackages, List.of(), List.of(normalPackage), List.of(priorityPackage));
        when(packageTemplateService.getActivePackageTemplateEntity(templateId))
                .thenReturn(expensive, normal, normal);
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionMapper.toPackageResponse(any(ExhibitionPackage.class))).thenReturn(null);

        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, request));
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, request));
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, request));
        assertNull(exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, request));
    }

    @Test
    void updatePackageCoversMissingOwnershipPriorityFloorAndSuccess() {
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        UUID oldId = UUID.randomUUID();
        UUID normalId = UUID.randomUUID();
        UUID priorityId = UUID.randomUUID();
        UUID belowId = UUID.randomUUID();
        UUID sameId = UUID.randomUUID();
        Exhibition otherExhibition = Exhibition.builder().id(99).build();
        ExhibitionPackage wrongOwner = ExhibitionPackage.builder().exhibition(otherExhibition).build();
        ExhibitionPackage changedDuplicate = ExhibitionPackage.builder().id(10)
                .exhibition(registrationExhibition)
                .template(PackageTemplate.builder().id(oldId).build()).build();
        ExhibitionPackage changedOk = ExhibitionPackage.builder().id(10)
                .exhibition(registrationExhibition)
                .template(PackageTemplate.builder().id(oldId).build()).build();
        ExhibitionPackage below = ExhibitionPackage.builder().id(10)
                .exhibition(registrationExhibition)
                .template(PackageTemplate.builder().id(belowId).build()).build();
        ExhibitionPackage same = ExhibitionPackage.builder().id(10)
                .exhibition(registrationExhibition)
                .template(PackageTemplate.builder().id(sameId).build()).build();
        PackageTemplate normal = PackageTemplate.builder().id(normalId).price(BigDecimal.TEN)
                .listingPriority(BoothListingPriority.NORMAL).build();
        PackageTemplate priority = PackageTemplate.builder().id(priorityId).price(BigDecimal.TEN)
                .listingPriority(BoothListingPriority.PRIORITY).build();
        PackageTemplate expensive = PackageTemplate.builder().id(belowId).price(BigDecimal.valueOf(11)).build();
        PackageTemplate sameTemplate = PackageTemplate.builder().id(sameId).price(BigDecimal.TEN).build();
        ExhibitionPackage otherNormal = ExhibitionPackage.builder().id(11)
                .template(PackageTemplate.builder().listingPriority(BoothListingPriority.NORMAL)
                        .build())
                .listingPrioritySnapshot(BoothListingPriority.NORMAL)
                .build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findById(10)).thenReturn(
                Optional.empty(), Optional.of(wrongOwner), Optional.of(changedDuplicate),
                Optional.of(changedOk), Optional.of(below), Optional.of(same));
        when(packageTemplateService.getActivePackageTemplateEntity(any())).thenReturn(
                normal, priority, expensive, sameTemplate);
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition))
                .thenReturn(List.of(changedDuplicate, otherNormal), List.of(changedOk, otherNormal));
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionMapper.toPackageResponse(any(ExhibitionPackage.class))).thenReturn(null);

        assertThrows(AppException.class, () -> exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder().build()));
        assertThrows(AppException.class, () -> exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder().build()));
        assertThrows(AppException.class, () -> exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder()
                        .templateId(normalId).finalPrice(BigDecimal.TEN).build()));
        assertNull(exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder()
                        .templateId(priorityId).finalPrice(BigDecimal.TEN).build()));
        assertThrows(AppException.class, () -> exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder()
                        .templateId(belowId).finalPrice(BigDecimal.TEN).build()));
        assertNull(exhibitionService.updateExhibitionPackage(
                organizer, exhibitionUuid, 10, ConfigureExhibitionPackageRequest.builder()
                        .templateId(sameId).finalPrice(BigDecimal.TEN).build()));
    }

    @Test
    void deletePackageCoversMissingOwnershipAndSuccess() {
        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        ExhibitionPackage wrongOwner = ExhibitionPackage.builder()
                .exhibition(Exhibition.builder().id(99).build()).build();
        ExhibitionPackage owned = ExhibitionPackage.builder().id(10).exhibition(registrationExhibition).build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findById(10))
                .thenReturn(Optional.empty(), Optional.of(wrongOwner), Optional.of(owned));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 10));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 10));
        assertNotNull(exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 10));
        verify(exhibitionPackageRepository).delete(owned);
    }

    @Test
    void createExhibitionCoversSponsorDateLeadTimeAndPendingLimits() {
        MultipartFile keyVisual = imageFile();
        MultipartFile logo = mock(MultipartFile.class);
        SponsorRequestDTO sponsor = SponsorRequestDTO.builder().name("Sponsor").build();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("New Expo").category("Tech")
                .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10).packages(List.of()).build();

        assertThrows(AppException.class, () -> exhibitionService.createExhibition(
                organizer, request, keyVisual, Collections.nCopies(16, logo)));
        request.setSponsors(Collections.nCopies(16, sponsor));
        assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, null));
        request.setSponsors(null);
        assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, List.of(logo)));

        request.setSponsors(List.of());
        request.setEndDate(request.getStartDate().minusDays(1));
        assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, List.of()));
        request.setEndDate(request.getStartDate().plusDays(5));
        when(timelinePolicy.hasMinimumLeadTime(request.getStartDate())).thenReturn(false, true);
        assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, List.of()));
        when(exhibitionRepository.countByOrganizerIdAndStatus(organizer.getId(), ExhibitionStatus.PENDING))
                .thenReturn(3L);
        assertThrows(AppException.class,
                () -> exhibitionService.createExhibition(organizer, request, keyVisual, List.of()));
    }

    @Test
    void createExhibitionUploadsSponsorLogo() {
        MultipartFile keyVisual = imageFile();
        MultipartFile logo = imageFile();
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest packageRequest = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId).finalPrice(BigDecimal.TEN).build();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name(" Sponsor Expo ").category(" Tech ")
                .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10).packages(List.of(packageRequest))
                .sponsors(List.of(SponsorRequestDTO.builder().name("Sponsor").build())).build();
        PackageTemplate template = PackageTemplate.builder().id(templateId).price(BigDecimal.ONE)
                .listingPriority(BoothListingPriority.NORMAL).build();
        when(packageTemplateService.getActivePackageTemplateEntity(templateId)).thenReturn(template);
        when(exhibitionRepository.save(any(Exhibition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudService.upload(keyVisual)).thenReturn(cloudResponse("key"));
        when(cloudService.upload(logo)).thenReturn(cloudResponse("sponsor"));
        when(exhibitionMapper.toResponse(any(Exhibition.class), anyList()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        assertNotNull(exhibitionService.createExhibition(organizer, request, keyVisual, List.of(logo)));
        request.setSponsors(List.of());
        assertNotNull(exhibitionService.createExhibition(organizer, request, keyVisual, List.of()));
        verify(cloudService).upload(logo);
    }

    @Test
    void updateExhibitionCoversAuthorizationDateLeadTimeAndDuplicateName() {
        User another = User.builder().id(UUID.randomUUID()).build();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Other").category("Tech")
                .startDate(LocalDate.now().plusDays(20)).endDate(LocalDate.now().plusDays(25))
                .estimatedBooths(10).packages(List.of()).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(another, exhibitionUuid, request,
                        null));
        request.setEndDate(request.getStartDate().minusDays(1));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, request,
                        null));
        request.setEndDate(request.getStartDate().plusDays(91));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, request,
                        null));
        request.setEndDate(request.getStartDate().plusDays(5));
        when(timelinePolicy.hasMinimumLeadTime(request.getStartDate())).thenReturn(false, true);
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, request,
                        null));
        when(exhibitionRepository.existsByNameIgnoreCase("Other")).thenReturn(true);
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, request,
                        null));
    }

    @Test
    void updatePendingExhibitionUploadsKeyVisual() {
        MultipartFile keyVisual = imageFile();
        UUID templateId = UUID.randomUUID();
        ConfigureExhibitionPackageRequest packageRequest = ConfigureExhibitionPackageRequest.builder()
                .templateId(templateId).finalPrice(BigDecimal.TEN).build();
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Expo 2026").category("Tech")
                .startDate(LocalDate.now().plusDays(20)).endDate(LocalDate.now().plusDays(25))
                .estimatedBooths(10).packages(List.of(packageRequest)).build();
        PackageTemplate template = PackageTemplate.builder().id(templateId).price(BigDecimal.ONE)
                .listingPriority(BoothListingPriority.NORMAL).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(packageTemplateService.getActivePackageTemplateEntity(templateId)).thenReturn(template);
        when(exhibitionRepository.save(any(Exhibition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudService.upload(keyVisual)).thenReturn(cloudResponse("updated-key"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionMapper.toResponse(any(Exhibition.class), anyList()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        assertNotNull(exhibitionService.updateExhibitionForOrganizer(
                organizer, exhibitionUuid, request, keyVisual));
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        assertNotNull(exhibitionService.updateExhibitionForOrganizer(
                organizer, exhibitionUuid, request, empty));
    }

    @Test
    void updateMediaAcceptsEmptyOptionalFiles() {
        MultipartFile emptyVideo = mock(MultipartFile.class);
        MultipartFile emptyFloor = mock(MultipartFile.class);
        MultipartFile emptyGuideline = mock(MultipartFile.class);
        when(emptyVideo.isEmpty()).thenReturn(true);
        when(emptyFloor.isEmpty()).thenReturn(true);
        when(emptyGuideline.isEmpty()).thenReturn(true);
        registrationExhibition.setStatus(ExhibitionStatus.ACTIVE);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of()))
                .thenReturn(ExhibitionResponseDTO.builder().build());

        assertNotNull(exhibitionService.updateExhibitionMedia(
                organizer, exhibitionUuid, emptyVideo, emptyFloor, emptyGuideline));
        assertNotNull(exhibitionService.updateExhibitionMedia(
                organizer, exhibitionUuid, null, null, null));
        verify(cloudService, never()).upload(any());
    }

    @Test
    void approveExhibitionCoversRoleStatusAndLifecycleEvents() {
        User nonAdmin = User.builder().id(UUID.randomUUID()).role(Role.ORGANIZER).build();
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        PackageTemplate template = PackageTemplate.builder().price(BigDecimal.ONE)
                .status(PackageTemplateStatus.ACTIVE).listingPriority(BoothListingPriority.NORMAL)
                .build();
        ExhibitionPackage pkg = ExhibitionPackage.builder().status(ExhibitionPackageStatus.ACTIVE)
                .template(template).priceSnapshot(BigDecimal.ONE)
                .listingPrioritySnapshot(BoothListingPriority.NORMAL)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitionResponseDTO dto = ExhibitionResponseDTO.builder().build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of(pkg));
        when(exhibitionRepository.save(registrationExhibition)).thenReturn(registrationExhibition);
        when(exhibitionMapper.toResponse(registrationExhibition, List.of(pkg))).thenReturn(dto);

        assertThrows(AppException.class, () -> exhibitionService.approveExhibition(nonAdmin, exhibitionUuid));
        registrationExhibition.setStatus(ExhibitionStatus.ACTIVE);
        assertThrows(AppException.class, () -> exhibitionService.approveExhibition(admin, exhibitionUuid));

        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(timelinePolicy.resolveTargetStatus(registrationExhibition, LocalDate.now()))
                .thenReturn(null, ExhibitionStatus.ACTIVE, ExhibitionStatus.COMPLETED);
        assertEquals(dto, exhibitionService.approveExhibition(admin, exhibitionUuid));
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        assertEquals(dto, exhibitionService.approveExhibition(admin, exhibitionUuid));
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        assertEquals(dto, exhibitionService.approveExhibition(admin, exhibitionUuid));
    }

    @Test
    void rejectExhibitionCoversRoleStatusAndNonTerminalRejection() {
        User nonAdmin = User.builder().id(UUID.randomUUID()).role(Role.ORGANIZER).build();
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        RejectExhibitionRequest request = RejectExhibitionRequest.builder().rejectedReason("Reason").build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        assertThrows(AppException.class,
                () -> exhibitionService.rejectExhibition(nonAdmin, exhibitionUuid, request));
        registrationExhibition.setStatus(ExhibitionStatus.ACTIVE);
        assertThrows(AppException.class,
                () -> exhibitionService.rejectExhibition(admin, exhibitionUuid, request));

        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setRejectionCount(0);
        when(exhibitionRepository.save(registrationExhibition)).thenReturn(registrationExhibition);
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of()))
                .thenReturn(ExhibitionResponseDTO.builder().build());
        assertNotNull(exhibitionService.rejectExhibition(admin, exhibitionUuid, request));
        assertEquals(1, registrationExhibition.getRejectionCount());
    }

    @Test
    void reviewMailSkipsMissingAddressesAndHandlesNullResult() {
        Exhibition exhibition = Exhibition.builder().build();
        ReflectionTestUtils.invokeMethod(exhibitionService, "sendExhibitionReviewMailSafely",
                exhibition, "Expo", null, null);
        exhibition.setOrganizer(User.builder().build());
        ReflectionTestUtils.invokeMethod(exhibitionService, "sendExhibitionReviewMailSafely",
                exhibition, "Expo", null, null);
        exhibition.setOrganizer(User.builder().email(" ").build());
        ReflectionTestUtils.invokeMethod(exhibitionService, "sendExhibitionReviewMailSafely",
                exhibition, "Expo", null, null);
        exhibition.setOrganizer(organizer);
        ReflectionTestUtils.invokeMethod(exhibitionService, "sendExhibitionReviewMailSafely",
                exhibition, "Expo", null, null);
    }

    @Test
    void sponsorOperationsCoverOwnershipLimitsMissingAssetsAndOptionalFields() {
        User another = User.builder().id(UUID.randomUUID()).build();
        MultipartFile file = imageFile();
        UUID assetId = UUID.randomUUID();
        ExhibitionAsset wrongExhibition = ExhibitionAsset.builder().id(assetId)
                .exhibition(Exhibition.builder().id(99).build())
                .type(ExhibitionAssetType.SPONSOR_LOGO).build();
        ExhibitionAsset wrongType = ExhibitionAsset.builder().id(assetId)
                .exhibition(registrationExhibition).type(ExhibitionAssetType.KEY_VISUAL).build();
        ExhibitionAsset valid = sponsorAsset("old");
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(another, exhibitionUuid, null, file));
        List<ExhibitionAsset> assets = new java.util.ArrayList<>();
        assets.add(ExhibitionAsset.builder().type(ExhibitionAssetType.KEY_VISUAL).build());
        for (int i = 0; i < 15; i++) {
            assets.add(ExhibitionAsset.builder().type(ExhibitionAssetType.SPONSOR_LOGO).build());
        }
        registrationExhibition.getAssets().clear();
        registrationExhibition.getAssets().addAll(assets);
        assertThrows(AppException.class,
                () -> exhibitionService.uploadSponsorLogo(organizer, exhibitionUuid, null, file));

        registrationExhibition.getAssets().clear();
        when(cloudService.upload(file)).thenReturn(cloudResponse("new"));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(List.of());
        when(exhibitionMapper.toResponse(registrationExhibition, List.of()))
                .thenReturn(ExhibitionResponseDTO.builder().build());
        assertNotNull(exhibitionService.uploadSponsorLogo(organizer, exhibitionUuid, null, file));

        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(another, exhibitionUuid, assetId, null,
                        null));
        when(exhibitionAssetRepository.findById(assetId)).thenReturn(
                Optional.empty(), Optional.of(wrongExhibition), Optional.of(wrongType),
                Optional.of(valid), Optional.of(valid));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, assetId, null,
                        null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, assetId, null,
                        null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, assetId, null,
                        null));
        assertNotNull(exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, assetId, null, null));
        assertNotNull(exhibitionService.updateSponsorLogo(organizer, exhibitionUuid, assetId, " ", empty));

        UUID deleteId = UUID.randomUUID();
        ExhibitionAsset deleteWrongExhibition = ExhibitionAsset.builder().id(deleteId)
                .exhibition(Exhibition.builder().id(99).build())
                .type(ExhibitionAssetType.SPONSOR_LOGO).build();
        ExhibitionAsset deleteWrongType = ExhibitionAsset.builder().id(deleteId)
                .exhibition(registrationExhibition).type(ExhibitionAssetType.KEY_VISUAL).build();
        ExhibitionAsset deleteValid = sponsorAsset("delete");
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(another, exhibitionUuid, deleteId));
        when(exhibitionAssetRepository.findById(deleteId)).thenReturn(
                Optional.empty(), Optional.of(deleteWrongExhibition), Optional.of(deleteWrongType),
                Optional.of(deleteValid));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, deleteId));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, deleteId));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, deleteId));
        assertNotNull(exhibitionService.deleteSponsorLogo(organizer, exhibitionUuid, deleteId));

        registrationExhibition.setStatus(ExhibitionStatus.REJECTED);
        ReflectionTestUtils.invokeMethod(exhibitionService,
                "validateSponsorChangesAllowed", registrationExhibition);
    }

    @Test
    void packageOperationsCoverUnauthorizedInvalidStatusAndDeleteInUse() {
        User another = User.builder().id(UUID.randomUUID()).build();
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(another, exhibitionUuid, null));
        assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionPackage(another, exhibitionUuid, 10, null));
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(another, exhibitionUuid, 10));

        registrationExhibition.setStatus(ExhibitionStatus.REGISTRATION);
        assertThrows(AppException.class,
                () -> exhibitionService.addExhibitionPackage(organizer, exhibitionUuid, null));
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitorRegistrationRepository.existsByExhibitionPackageId(10)).thenReturn(true);
        assertThrows(AppException.class,
                () -> exhibitionService.deleteExhibitionPackage(organizer, exhibitionUuid, 10));
    }

    @Test
    void cloudCleanupCoversEmptyIdsInactiveTransactionsAndDeleteFailure() {
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetOnRollback", null, "image");
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetOnRollback", " ", "image");
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetOnRollback", "inactive", "image");
        TransactionSynchronizationManager.initSynchronization();
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetOnRollback", "no-tx", "image");
        TransactionSynchronizationManager.clearSynchronization();

        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetAfterCommit", null, "image");
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetAfterCommit", " ", "image");
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetAfterCommit", "inactive", "image");
        TransactionSynchronizationManager.initSynchronization();
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAssetAfterCommit", "no-tx", "image");
        TransactionSynchronizationManager.clearSynchronization();

        org.mockito.Mockito.doThrow(new RuntimeException("cloud"))
                .when(cloudService).delete("failure", "image");
        ReflectionTestUtils.invokeMethod(exhibitionService, "deleteCloudAsset", "failure", "image");
    }

    @Test
    void publishExhibitionRejectsStartDate() {
        registrationExhibition.setStatus(ExhibitionStatus.REGISTRATION);
        registrationExhibition.setStartDate(LocalDate.now());
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitionService.publishExhibition(organizer, exhibitionUuid));
        assertEquals(ErrorCode.EXHIBITION_ALREADY_STARTED, exception.getErrorCode());
    }
}
