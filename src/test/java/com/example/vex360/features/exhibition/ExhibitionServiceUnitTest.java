package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

import com.example.vex360.features.analytics.repositories.AnalyticsEventRepository;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.request.AdminExhibitionStatusFilter;
import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionAsset;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.impl.ExhibitionServiceImpl;
import com.example.vex360.features.user.repositories.UserRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.ExhibitionAssetType;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import com.example.vex360.shared.enums.Role;
import com.example.vex360.features.exhibition.services.ExhibitionReviewHistoryService;

@ExtendWith(MockitoExtension.class)
class ExhibitionServiceUnitTest {

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitorRegistrationRepository exhibitorRegistrationRepository;

    @Mock
    private BoothRepository boothRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ExhibitionPackageRepository exhibitionPackageRepository;

    @Mock
    private ExhibitionAssetRepository exhibitionAssetRepository;

    @Mock
    private CloudService cloudService;

    @Mock
    private ExhibitionMapper exhibitionMapper;

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExhibitionReviewHistoryService reviewHistoryService;

    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;

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
        when(companyRepository.findByOwnerUserId(organizer.getId())).thenReturn(Optional.of(company));

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid);

        assertEquals(exhibitionUuid, result.getUuid());
        assertEquals(registrationExhibition.getName(), result.getName());
        assertEquals(organizer.getFullName(), result.getOrganizerName());
        assertEquals(company.getName(), result.getOrganizationName());
        assertEquals(company.getEmail(), result.getEmail());
        assertEquals(company.getPhone(), result.getPhone());
        verify(companyRepository).findByOwnerUserId(organizer.getId());
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
        when(companyRepository.findByOwnerUserId(organizer.getId())).thenReturn(Optional.empty());

        ExhibitionResponseDTO result = exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid);

        assertEquals(mappedResponse, result);
        assertNull(result.getOrganizationName());
        assertNull(result.getEmail());
        assertNull(result.getPhone());
        verify(companyRepository).findByOwnerUserId(organizer.getId());
    }

    @Test
    void getSummaryForOrganizerAddsPendingRegistrationAndBoothReviewCounts() {
        when(exhibitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId()))
                .thenReturn(List.of(registrationExhibition));
        when(exhibitorRegistrationRepository.countActionRequiredGroupedByExhibition(
                List.of(registrationExhibition.getId()),
                List.of(ExhibitorRegistrationStatus.PENDING, ExhibitorRegistrationStatus.PENDING_PAYMENT)))
                .thenReturn(List.<Object[]>of(new Object[] { registrationExhibition.getId(), 3L }));
        when(boothRepository.countBoothsGroupedByExhibitionAndStatus(
                List.of(registrationExhibition.getId()), BoothStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[] { registrationExhibition.getId(), 2L }));

        var summary = exhibitionService.getSummaryForOrganizer(organizer);

        assertEquals(3L, summary.getPendingRegistrationCount());
        assertEquals(2L, summary.getPendingBoothReviewCount());
        assertEquals(5L, summary.getTotalCount());
    }

    @Test
    void getSummariesByExhibitionIncludesExhibitionsWithZeroCounts() {
        Exhibition secondExhibition = Exhibition.builder()
                .id(2)
                .uuid(UUID.randomUUID())
                .name("Expo 2027")
                .organizer(organizer)
                .build();
        List<Integer> exhibitionIds = List.of(registrationExhibition.getId(), secondExhibition.getId());
        when(exhibitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId()))
                .thenReturn(List.of(registrationExhibition, secondExhibition));
        when(exhibitorRegistrationRepository.countActionRequiredGroupedByExhibition(
                exhibitionIds,
                List.of(ExhibitorRegistrationStatus.PENDING, ExhibitorRegistrationStatus.PENDING_PAYMENT)))
                .thenReturn(List.<Object[]>of(new Object[] { registrationExhibition.getId(), 4L }));
        when(boothRepository.countBoothsGroupedByExhibitionAndStatus(exhibitionIds, BoothStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[] { secondExhibition.getId(), 1L }));

        var summaries = exhibitionService.getSummariesByExhibitionForOrganizer(organizer);

        assertEquals(2, summaries.size());
        assertEquals(exhibitionUuid, summaries.get(0).getExhibitionUuid());
        assertEquals(4L, summaries.get(0).getPendingRegistrationCount());
        assertEquals(0L, summaries.get(0).getPendingBoothReviewCount());
        assertEquals(4L, summaries.get(0).getTotalCount());
        assertEquals(secondExhibition.getUuid(), summaries.get(1).getExhibitionUuid());
        assertEquals(0L, summaries.get(1).getPendingRegistrationCount());
        assertEquals(1L, summaries.get(1).getPendingBoothReviewCount());
        assertEquals(1L, summaries.get(1).getTotalCount());
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
        Pageable requestedPageable = PageRequest.of(
                1, 10, Sort.by(
                        Sort.Order.asc("organizerName"),
                        Sort.Order.desc("expectedBoothCount"),
                        Sort.Order.asc("exhibitionName")));
        Pageable mappedPageable = PageRequest.of(
                1, 10, Sort.by(
                        Sort.Order.asc("organizer.fullName"),
                        Sort.Order.desc("estimatedBooths"),
                        Sort.Order.asc("name")));
        List<ExhibitionStatus> approvedStatuses = List.of(
                ExhibitionStatus.REGISTRATION,
                ExhibitionStatus.PUBLISHED,
                ExhibitionStatus.ACTIVE,
                ExhibitionStatus.COMPLETED);
        Page<Exhibition> page = new PageImpl<>(List.of(registrationExhibition), mappedPageable, 11);
        when(exhibitionRepository.searchAdminExhibitions(
                "Expo", approvedStatuses, "Tech", null, null, mappedPageable))
                .thenReturn(page);
        when(exhibitionMapper.toResponse(registrationExhibition))
                .thenReturn(ExhibitionResponseDTO.builder().name("Expo 2026").build());

        PageResponse<ExhibitionResponseDTO> result = exhibitionService.searchExhibitionsForAdmin(
                " Expo ", AdminExhibitionStatusFilter.APPROVED, " Tech ",
                null, null, requestedPageable);

        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(11, result.getTotalElements());
        assertEquals("Expo 2026", result.getContent().get(0).getName());
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
        when(exhibitionRepository.searchAdminExhibitions(
                null, allStatuses, null, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        exhibitionService.searchExhibitionsForAdmin(null, null, null, null, null, pageable);

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
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
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
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("New Expo")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(10)
                .build();
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
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
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
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
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
        registrationExhibition.setStatus(ExhibitionStatus.PUBLISHED);
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
    void updateExhibitionMedia_pendingStatus_throwsAssetChangesNotAllowed() {
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.updateExhibitionMedia(organizer, exhibitionUuid, null, null, null));
        assertEquals(ErrorCode.EXHIBITION_ASSET_CHANGES_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void approveExhibition_leadTimeNotMet_throwsLeadTimeNotMet() {
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        registrationExhibition.setStatus(ExhibitionStatus.PENDING);
        registrationExhibition.setStartDate(LocalDate.now().plusDays(2));
        when(exhibitionRepository.findByUuid(exhibitionUuid)).thenReturn(Optional.of(registrationExhibition));
        when(timelinePolicy.hasMinimumLeadTime(any())).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitionService.approveExhibition(admin, exhibitionUuid));
        assertEquals(ErrorCode.EXHIBITION_APPROVAL_LEAD_TIME_NOT_MET, ex.getErrorCode());
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
}
