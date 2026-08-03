package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import com.example.vex360.features.mail.AfterCommitExecutor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
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

import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.request.AdminExhibitionStatusFilter;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
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
        Page<Exhibition> page = new PageImpl<>(List.of(publishedExhibition), pageable, 1);

        List<ExhibitionStatus> expectedStatuses = List.of(
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);

        when(exhibitionRepository.searchExhibitions(
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
    }

    @Test
    void searchExhibitionsForVisitorFiltersExactPublicStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Exhibition> page = new PageImpl<>(List.of(), pageable, 0);
        when(exhibitionRepository.searchExhibitions(
                null, List.of(ExhibitionStatus.ACTIVE), null, null, null, pageable))
                .thenReturn(page);

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForVisitor(
                " ", ExhibitionStatus.ACTIVE, " ", null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(exhibitionRepository).searchExhibitions(
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
        verify(exhibitionRepository, never()).searchExhibitions(
                any(), any(), any(), any(), any(), any());
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
        when(exhibitionPackageRepository.findByExhibition(any(Exhibition.class))).thenReturn(Collections.emptyList());

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
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("startDate")));
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
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
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
        when(exhibitionRepository.save(any(Exhibition.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
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
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null));
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
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null));
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
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null));
        assertEquals(ErrorCode.EXHIBITION_ALREADY_STARTED, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizer_hasRegistrations_throwsHasRegistrations() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now().plusDays(20));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository.existsByExhibitionPackageExhibitionId(registrationExhibition.getId()))
                .thenReturn(true);

        CreateExhibitionRequest req = CreateExhibitionRequest.builder()
                .name("New Name")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(25))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null));
        assertEquals(ErrorCode.EXHIBITION_HAS_REGISTRATIONS, ex.getErrorCode());
    }

    @Test
    void updateExhibitionForOrganizerUsesTimelinePolicyDate() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.of(2026, 1, 11));
        when(timelinePolicy.today()).thenReturn(LocalDate.of(2026, 1, 10));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(exhibitorRegistrationRepository.existsByExhibitionPackageExhibitionId(registrationExhibition.getId()))
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
                () -> exhibitionService.updateExhibitionMedia(organizer, exhibitionUuid, null, null, null));
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
        when(exhibitionRepository.save(any(Exhibition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exhibitionPackageRepository.findByExhibition(registrationExhibition)).thenReturn(Collections.emptyList());

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
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class))).thenAnswer(inv -> inv.getArgument(0));
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

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
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
                () -> exhibitionService.updateExhibitionForOrganizer(organizer, exhibitionUuid, req, null));

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
}
