package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.PaymentStatus;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;

import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.CommissionCalculator;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.impl.ExhibitorRegistrationServiceImpl;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionPaymentCompletedEvent;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.BoothListingPriority;

import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class ExhibitorRegistrationServiceTest {

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private ExhibitionPackageRepository packageRepository;

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private UserService userService;

    @Mock
    private CompanyService companyService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOSIntegrationService payOSIntegrationService;

    @Mock
    private PaymentFulfillmentService paymentFulfillmentService;

    @Mock
    private BoothProvisioningService boothProvisioningService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MailService mailService;

    @Spy
    private AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();

    @Mock
    private CommissionCalculator commissionCalculator;

    @Mock
    private Clock clock;

    @InjectMocks
    private ExhibitorRegistrationServiceImpl registrationService;

    private User companyUser;
    private Company company;
    private ExhibitionPackage paidPackage;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(registrationService, "returnUrl", "http://localhost:5173/payment/success");
        ReflectionTestUtils.setField(registrationService, "cancelUrl", "http://localhost:5173/payment/cancel");
        Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-10T00:00:00Z"));
        Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Mockito.lenient().when(commissionCalculator.calculateCommission(any(), any())).thenAnswer(inv -> {
            BigDecimal amt = inv.getArgument(0);
            return new CommissionCalculator.CommissionResult(
                    amt, BigDecimal.ZERO, amt, 0);
        });
        ReflectionTestUtils.setField(registrationService, "timelinePolicy", new ExhibitionTimelinePolicy(clock));

        companyUser = User.builder()
                .id(UUID.randomUUID())
                .email("company@example.com")
                .fullName("Test Company")
                .build();

        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Test Company")
                .ownerUser(companyUser)
                .build();

        PackageTemplate template = PackageTemplate.builder()
                .id(UUID.randomUUID())
                .name("Standard Package")
                .price(BigDecimal.valueOf(1000000))
                .build();

        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Expo")
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.JANUARY, 20))
                .build();
        Mockito.lenient().when(exhibitionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(exhibition));

        paidPackage = ExhibitionPackage.builder()
                .id(10)
                .template(template)
                .exhibition(exhibition)
                .finalPrice(BigDecimal.valueOf(1500000)) // finalPrice >= floorPrice (1M)
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();

        ExhibitionPackage.builder()
                .id(11)
                .template(PackageTemplate.builder()
                        .id(UUID.randomUUID())
                        .price(BigDecimal.ZERO)
                        .build())
                .exhibition(exhibition)
                .finalPrice(BigDecimal.ZERO)
                .build();

    }

    @Test
    void testInitializeRegistration_Success() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(false);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10,
                "Join to meet buyers", "Test Booth", "Test Booth Description");

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.PENDING, registration.getStatus());
        assertEquals(company, registration.getCompany());
        assertEquals(paidPackage, registration.getExhibitionPackage());
        assertEquals("Test Booth", registration.getBoothName());
        assertEquals("Test Booth Description", registration.getBoothDescription());

        verify(userService).getUserEntityById(companyUser.getId());
        verify(companyService).getCompanyEntityForCurrentUserForUpdate(companyUser);
        verify(packageRepository).findById(10);
        verify(exhibitionRepository).findByIdForUpdate(1);
        verify(registrationRepository).save(any(ExhibitorRegistration.class));
    }

    @Test
    void initializeRegistration_usesExhibitionPackageSnapshotWhenTemplateChanged() {
        paidPackage.getTemplate().setListingPriority(BoothListingPriority.FEATURED);
        paidPackage.snapshotTemplateTerms(paidPackage.getTemplate());
        paidPackage.getTemplate().setName("Changed Package");
        paidPackage.getTemplate().setPrice(BigDecimal.TEN);
        paidPackage.getTemplate().setListingPriority(BoothListingPriority.NORMAL);
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(false);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10,
                "Join expo", "Test Booth", "Test Booth Description");

        assertEquals("Standard Package", registration.getPackageNameSnapshot());
        assertEquals(BigDecimal.valueOf(1000000), registration.getPriceSnapshot());
        assertEquals(paidPackage.getListingPrioritySnapshot(), registration.getListingPrioritySnapshot());
    }

    @Test
    void initializeRegistration_savesTrimmedParticipationReasonAndBoothInfo() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(false);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10,
                "  Meet partners and launch products  ", "  Trimmed Booth  ", "  Trimmed Description  ");

        assertEquals("Meet partners and launch products", registration.getParticipationReason());
        assertEquals("Trimmed Booth", registration.getBoothName());
        assertEquals("Trimmed Description", registration.getBoothDescription());
    }

    @Test
    void initializeRegistration_whenExhibitionStatusIsPublished_success() {
        paidPackage.getExhibition().setStatus(ExhibitionStatus.PUBLISHED);
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(false);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10,
                "Join published expo", "Test Booth", "Test Booth Description");

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.PENDING, registration.getStatus());
    }

    @Test
    void initializeRegistration_duplicateActiveRegistration_throwsRegistrationAlreadyExists() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.initializeRegistration(companyUser.getId(), 10, "Join expo", "Test Booth",
                        "Test Booth Description"));

        assertEquals(ErrorCode.REGISTRATION_ALREADY_EXISTS, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void initializeRegistration_rejectedOrCanceledExistingRegistration_allowsNewRegistration() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.existsActiveRegistration(eq(company.getId()), eq(1), any()))
                .thenReturn(false);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10,
                "Register again after previous request ended", "Test Booth", "Test Booth Description");

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.PENDING, registration.getStatus());
    }

    @Test
    void testInitializeRegistration_PackageNotFound_ThrowsException() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(999)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.initializeRegistration(companyUser.getId(), 999, "Join expo", "Test Booth",
                    "Test Booth Description");
        });

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void testInitializeRegistration_InvalidExhibitionStatus_ThrowsException() {
        Exhibition pendingExhibition = Exhibition.builder()
                .id(2)
                .name("Pending Expo")
                .status(ExhibitionStatus.PENDING)
                .startDate(LocalDate.of(2026, Month.JANUARY, 20))
                .build();
        ExhibitionPackage pendingPackage = ExhibitionPackage.builder()
                .id(12)
                .template(paidPackage.getTemplate())
                .exhibition(pendingExhibition)
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();

        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(12)).thenReturn(Optional.of(pendingPackage));
        when(exhibitionRepository.findByIdForUpdate(2)).thenReturn(Optional.of(pendingExhibition));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.initializeRegistration(companyUser.getId(), 12, "Join expo", "Test Booth",
                    "Test Booth Description");
        });

        assertEquals(ErrorCode.REGISTRATION_CLOSED, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ExhibitionStatus.class, names = { "ACTIVE", "COMPLETED" })
    void initializeRegistration_afterRegistrationPhase_throwsInvalidStatus(ExhibitionStatus status) {
        paidPackage.getExhibition().setStatus(status);
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.initializeRegistration(companyUser.getId(), 10, "Join expo", "Test Booth",
                        "Test Booth Description"));

        assertEquals(ErrorCode.REGISTRATION_CLOSED, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void initializeRegistration_afterBoothReviewDeadline_throwsInvalidStatus() {
        paidPackage.getExhibition().setStartDate(LocalDate.of(2026, Month.JANUARY, 12));
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.initializeRegistration(companyUser.getId(), 10, "Join expo", "Test Booth",
                        "Test Booth Description"));

        assertEquals(ErrorCode.REGISTRATION_CLOSED, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void initializeRegistration_inactivePackage_throwsValidationFailed() {
        paidPackage.setStatus(ExhibitionPackageStatus.INACTIVE);
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.initializeRegistration(companyUser.getId(), 10, "Join expo", "Test Booth",
                        "Test Booth Description"));

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_INACTIVE, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void testGetRegistrationDetails_RegistrationNotFound_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());
        });

        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_UnauthorizedUser_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        Company anotherCompany = Company.builder().id(UUID.randomUUID()).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .company(anotherCompany)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());
        });

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_Success() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("chkUrl", dto.getCheckoutUrl());
        assertEquals("PENDING", dto.getPaymentStatus());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_FailedPayment() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment failedPayment = Payment.builder()
                .id(100)
                .status(PaymentStatus.FAILED)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(failedPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("chkUrl", dto.getCheckoutUrl());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_LongDescription() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitionPackage longPackage = ExhibitorRegistrationServiceTest.this.paidPackage;
        longPackage.getExhibition().setName("Tech Exhibition Show Expo Event 2026 Very Long Name");
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(longPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("PayOS Link Generation Failed"));

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        Assertions.assertNull(dto.getCheckoutUrl());
        assertEquals("PENDING", dto.getPaymentStatus());
    }

    @Test
    void getRegistrationDetails_databaseFailureIsNotHidden() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> registrationService.getRegistrationDetails(registrationUuid, companyUser.getId()));
    }

    @Test
    void getRegistrationDetails_pendingPaymentWithoutCheckoutUrl_regeneratesLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        Payment stuckPayment = Payment.builder()
                .id(100)
                .status(PaymentStatus.PENDING)
                .checkoutUrl(null)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(stuckPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreatePaymentLinkResponse response = mock(CreatePaymentLinkResponse.class);
        when(response.getCheckoutUrl()).thenReturn("regenerated-url");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(response);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertEquals(PaymentStatus.FAILED, stuckPayment.getStatus());
        assertEquals("regenerated-url", dto.getCheckoutUrl());
    }

    @Test
    void testGetRegistrationDetails_MissingCompany_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(null)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException ex = assertThrows(AppException.class,
                () -> registrationService.getRegistrationDetails(registrationUuid, companyUser.getId()));
        assertEquals(ErrorCode.REGISTRATION_COMPANY_MISSING, ex.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_MissingPackage_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(null)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException ex = assertThrows(AppException.class,
                () -> registrationService.getRegistrationDetails(registrationUuid, companyUser.getId()));
        assertEquals(ErrorCode.REGISTRATION_PACKAGE_MISSING, ex.getErrorCode());
    }

    @Test
    void testGetRegistrationsForOrganizer_Unauthenticated_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForOrganizer(null, UUID.randomUUID(),
                    ExhibitorRegistrationStatus.PENDING, "", Pageable.unpaged());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        User userNoId = User.builder().id(null).build();
        AppException exception2 = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForOrganizer(userNoId, UUID.randomUUID(),
                    ExhibitorRegistrationStatus.PENDING, "", Pageable.unpaged());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception2.getErrorCode());
    }

    @Test
    void testGetRegistrationsForOrganizer_EmptyList_ReturnsPage() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        var pageResponse = registrationService.getRegistrationsForOrganizer(organizer, exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest);

        assertNotNull(pageResponse);
        assertEquals(0, pageResponse.getContent().size());
    }

    @Test
    void testGetRegistrationsForOrganizer_NormalizesKeywordAndPreservesMultiSort() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(1, 10, Sort.by(
                Sort.Order.asc("company.fullName"),
                Sort.Order.desc("submittedAt"),
                Sort.Order.asc("status")));
        when(registrationRepository.searchForOrganizer(
                organizer.getId(), exhibitionUuid, ExhibitorRegistrationStatus.APPROVED, "Acme", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 25));

        var response = registrationService.getRegistrationsForOrganizer(
                organizer, exhibitionUuid, ExhibitorRegistrationStatus.APPROVED, "  Acme  ", pageRequest);

        assertEquals(1, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(25, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        verify(registrationRepository).searchForOrganizer(
                organizer.getId(), exhibitionUuid, ExhibitorRegistrationStatus.APPROVED, "Acme", pageRequest);
    }

    @Test
    void testGetRegistrationsForOrganizer_BlankKeywordDisablesSearch() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(registrationRepository.searchForOrganizer(
                organizer.getId(), exhibitionUuid, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        registrationService.getRegistrationsForOrganizer(
                organizer, exhibitionUuid, null, "   ", pageRequest);

        verify(registrationRepository).searchForOrganizer(
                organizer.getId(), exhibitionUuid, null, null, pageRequest);
    }

    @Test
    void testGetRegistrationsForOrganizer_WithPayments_ReturnsPage() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        ExhibitorRegistration reg1 = ExhibitorRegistration.builder().id(1).uuid(UUID.randomUUID()).company(company)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();
        ExhibitorRegistration reg2 = ExhibitorRegistration.builder().id(2).uuid(UUID.randomUUID()).company(company)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(new PageImpl<>(List.of(reg1, reg2), pageRequest, 2));

        Payment payment1New = Payment.builder().id(101).status(PaymentStatus.PENDING).exhibitorRegistration(reg1)
                .createdAt(Instant.now()).build();
        Payment payment2 = Payment.builder().id(102).status(PaymentStatus.PAID).exhibitorRegistration(reg2)
                .createdAt(Instant.now()).build();

        when(paymentRepository.findLatestPaymentsByRegistrationIds(List.of(1, 2)))
                .thenReturn(List.of(payment1New, payment2));

        var pageResponse = registrationService.getRegistrationsForOrganizer(organizer, exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest);

        assertNotNull(pageResponse);
        assertEquals(2, pageResponse.getContent().size());
        assertEquals("PENDING", pageResponse.getContent().get(0).getPaymentStatus());
        assertEquals("PAID", pageResponse.getContent().get(1).getPaymentStatus());
    }

    @Test
    void testApproveRegistration_Unauthenticated_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(null, UUID.randomUUID());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_NotFound_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, registrationUuid);
        });
        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_UnauthorizedOrganizer_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        User differentOrganizer = User.builder().id(UUID.randomUUID()).build();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(differentOrganizer).name("Expo").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, registrationUuid);
        });
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_NotPending_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo")
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.JANUARY, 20))
                .build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).status(ExhibitorRegistrationStatus.APPROVED).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, registrationUuid);
        });
        assertEquals(ErrorCode.REGISTRATION_INVALID_STATUS, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_PaidPackage_StatusSetToPendingPayment() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo")
                .status(ExhibitionStatus.REGISTRATION).startDate(LocalDate.of(2026, Month.JANUARY, 20)).build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING)
                .packageNameSnapshot("Snapshot Package")
                .finalPriceSnapshot(BigDecimal.valueOf(8))
                .currencySnapshot("USD")
                .build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.approveRegistration(organizer, registrationUuid);

        assertNotNull(dto);
        assertEquals("PENDING_PAYMENT", dto.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(mailService).sendExhibitorRegistrationReviewResultEmail(
                eq("company@example.com"),
                eq("Test Company"),
                eq("Test Company"),
                eq("Expo"),
                eq("Snapshot Package"),
                eq(BigDecimal.valueOf(8)),
                eq("USD"),
                eq(ExhibitorRegistrationStatus.PENDING_PAYMENT),
                eq(null));
    }

    @Test
    void testApproveRegistration_FreePackage_StatusSetToApproved_CreateFreePayment() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo")
                .status(ExhibitionStatus.REGISTRATION).startDate(LocalDate.of(2026, Month.JANUARY, 20)).build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.ZERO).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.approveRegistration(organizer, registrationUuid);

        assertNotNull(dto);
        assertEquals("APPROVED", dto.getStatus());
        verify(paymentRepository).save(any(Payment.class));
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void testRejectRegistration_Success() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING_PAYMENT).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.rejectRegistration(organizer, registrationUuid, "Invalid docs");

        assertNotNull(dto);
        assertEquals("REJECTED", dto.getStatus());
        assertEquals("Invalid docs", dto.getRejectedReason());
    }

    @Test
    void rejectRegistration_blankReason_throwsValidationFailed() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.rejectRegistration(organizer, registrationUuid, "   "));

        assertEquals(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void mapToResponse_includesParticipationReasonAndPackageSnapshots() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .participationReason("Meet buyers")
                .priceSnapshot(BigDecimal.valueOf(1000000))
                .finalPriceSnapshot(BigDecimal.valueOf(1500000))
                .currencySnapshot("VND")
                .maxProductsPerBoothSnapshot(12)
                .maxEmbeddedVideosPerBoothSnapshot(3)
                .maxPanoramasPerBoothSnapshot(4)
                .maxHotspotsPerBoothSnapshot(25)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertEquals("Meet buyers", dto.getParticipationReason());
        assertEquals(BigDecimal.valueOf(1000000), dto.getPriceSnapshot());
        assertEquals(BigDecimal.valueOf(1500000), dto.getFinalPriceSnapshot());
        assertEquals("VND", dto.getCurrencySnapshot());
        assertEquals(12, dto.getMaxProductsPerBoothSnapshot());
        assertEquals(3, dto.getMaxEmbeddedVideosPerBoothSnapshot());
        assertEquals(4, dto.getMaxPanoramasPerBoothSnapshot());
        assertEquals(25, dto.getMaxHotspotsPerBoothSnapshot());
    }

    @Test
    void testGetRegistrationDetails_StatusNotPendingPayment_DoesNotGeneratePaymentLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        Assertions.assertNull(dto.getCheckoutUrl());
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_PaymentPending_DoesNotGeneratePaymentLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment pendingPayment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("oldCheckoutUrl")
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(pendingPayment));

        PaymentLink mockLink = mock(PaymentLink.class);
        when(mockLink.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(payOSIntegrationService.getPaymentLinkInformation(123456L)).thenReturn(mockLink);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("oldCheckoutUrl", dto.getCheckoutUrl());
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void getRegistrationDetails_payOSLookupFails_keepsPendingPayment() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        Payment pendingPayment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("existing-checkout-url")
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(pendingPayment));
        when(payOSIntegrationService.getPaymentLinkInformation(123456L))
                .thenThrow(new RuntimeException("PayOS timeout"));

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        assertEquals("existing-checkout-url", dto.getCheckoutUrl());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void getRegistrationDetails_cancelledPayOSLink_regeneratesNewLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment pendingPayment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("cancelledCheckoutUrl")
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentLink mockLink = mock(PaymentLink.class);
        when(mockLink.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        when(payOSIntegrationService.getPaymentLinkInformation(123456L)).thenReturn(mockLink);

        CreatePaymentLinkResponse response = mock(CreatePaymentLinkResponse.class);
        when(response.getCheckoutUrl()).thenReturn("fresh-new-checkout-url");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(response);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
        assertEquals("fresh-new-checkout-url", dto.getCheckoutUrl());
        verify(payOSIntegrationService).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void testGetRegistrationsForOrganizer_OlderPaymentSkipped() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        ExhibitorRegistration reg1 = ExhibitorRegistration.builder().id(1).uuid(UUID.randomUUID()).company(company)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(new PageImpl<>(List.of(reg1), pageRequest, 1));

        Instant now = Instant.now();
        Payment payment1New = Payment.builder().id(101).status(PaymentStatus.PENDING).exhibitorRegistration(reg1)
                .createdAt(now).build();

        when(paymentRepository.findLatestPaymentsByRegistrationIds(List.of(1)))
                .thenReturn(List.of(payment1New));

        var pageResponse = registrationService.getRegistrationsForOrganizer(organizer, exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest);

        assertNotNull(pageResponse);
        assertEquals(1, pageResponse.getContent().size());
        assertEquals("PENDING", pageResponse.getContent().get(0).getPaymentStatus());
    }

    @Test
    void testRejectRegistration_NotPendingOrPendingPayment_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.APPROVED).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(organizer, registrationUuid, "Invalid docs");
        });
        assertEquals(ErrorCode.REGISTRATION_INVALID_STATUS, exception.getErrorCode());
    }

    @Test
    void testRejectRegistration_StatusPending_Success() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.rejectRegistration(organizer, registrationUuid, "Invalid docs");

        assertNotNull(dto);
        assertEquals("REJECTED", dto.getStatus());
        assertEquals("Invalid docs", dto.getRejectedReason());
    }

    @Test
    void testApproveRegistration_OrganizerIdNull_ThrowsException() {
        User organizer = User.builder().id(null).build();
        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, UUID.randomUUID());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void testRejectRegistration_Unauthenticated_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(null, UUID.randomUUID(), "reason");
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        User userNoId = User.builder().id(null).build();
        AppException exception2 = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(userNoId, UUID.randomUUID(), "reason");
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception2.getErrorCode());
    }

    @Test
    void testRejectRegistration_NotFound_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(organizer, registrationUuid, "reason");
        });
        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testRejectRegistration_UnauthorizedOrganizer_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        User differentOrganizer = User.builder().id(UUID.randomUUID()).build();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(differentOrganizer).name("Expo").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(organizer, registrationUuid, "reason");
        });
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationsForExhibitor_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();
        Page<ExhibitorRegistration> page = new PageImpl<>(List.of(registration), pageable, 1);

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.searchForExhibitor(
                company.getId(), ExhibitorRegistrationStatus.PENDING, "Expo", pageable))
                .thenReturn(page);

        PageResponse<ExhibitorRegistrationResponseDTO> result = registrationService.getRegistrationsForExhibitor(
                companyUser, ExhibitorRegistrationStatus.PENDING, "  Expo  ", pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("PENDING", result.getContent().get(0).getStatus());
    }

    @Test
    void getRegistrationsForExhibitor_pendingPaymentIsReadOnly() {
        Pageable pageable = PageRequest.of(0, 10);
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        Payment payment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .exhibitorRegistration(registration)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("existing-checkout-url")
                .build();
        Page<ExhibitorRegistration> page = new PageImpl<>(List.of(registration), pageable, 1);

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.searchForExhibitor(company.getId(), null, null, pageable)).thenReturn(page);
        when(paymentRepository.findLatestPaymentsByRegistrationIds(List.of(1))).thenReturn(List.of(payment));

        PageResponse<ExhibitorRegistrationResponseDTO> result = registrationService.getRegistrationsForExhibitor(
                companyUser, null, null, pageable);

        assertEquals("existing-checkout-url", result.getContent().get(0).getCheckoutUrl());
        verify(payOSIntegrationService, never()).getPaymentLinkInformation(any());
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testGetRegistrationsForExhibitor_Unauthenticated_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);

        // exhibitor is null
        AppException ex1 = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForExhibitor(null, null, null, pageable);
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, ex1.getErrorCode());

        // exhibitor ID is null
        User userNoId = User.builder().id(null).build();
        AppException ex2 = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForExhibitor(userNoId, null, null, pageable);
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, ex2.getErrorCode());
    }

    @Test
    void testCancelRegistration_Success() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment pendingPayment = Payment.builder()
                .id(10)
                .exhibitorRegistration(registration)
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(1)).thenReturn(List.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        ExhibitorRegistrationResponseDTO result = registrationService.cancelRegistration(companyUser, registrationUuid);

        assertNotNull(result);
        assertEquals("CANCELED", result.getStatus());
        assertEquals("FAILED", result.getPaymentStatus());
        verify(payOSIntegrationService).cancelPaymentLink(123456L, "Exhibitor canceled registration");
    }

    @Test
    void testCancelRegistration_PaymentAlreadyPaid_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment paidPayment = Payment.builder()
                .id(10)
                .exhibitorRegistration(registration)
                .orderCode(123456L)
                .status(PaymentStatus.PAID)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(1)).thenReturn(List.of(paidPayment));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.cancelRegistration(companyUser, registrationUuid);
        });

        assertEquals(ErrorCode.REGISTRATION_ALREADY_PAID, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_PaidAtPayOS_DelegatesFulfillmentAndPublishesCompletion() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment pendingPayment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("")
                .build();

        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(pendingPayment));

        PaymentLink mockLink = mock(PaymentLink.class);
        when(mockLink.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(payOSIntegrationService.getPaymentLinkInformation(123456L)).thenReturn(mockLink);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.PAID));
        verify(paymentFulfillmentService).processFulfillmentForOrderCode(123456L);
        verify(eventPublisher).publishEvent(any(ExhibitionPaymentCompletedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void testCancelRegistration_Unauthorized_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        Company differentCompany = Company.builder().id(UUID.randomUUID()).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(differentCompany)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.cancelRegistration(companyUser, registrationUuid);
        });
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testCancelRegistration_InvalidStatus_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(company)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.cancelRegistration(companyUser, registrationUuid);
        });
        assertEquals(ErrorCode.EXHIBITION_CANNOT_CANCEL, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_UsesFinalPriceSnapshot() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo")
                .status(ExhibitionStatus.REGISTRATION)
                .startDate(LocalDate.of(2026, Month.JANUARY, 20))
                .build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.valueOf(9999)) // Changed package price
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(company).status(ExhibitorRegistrationStatus.PENDING)
                .finalPriceSnapshot(BigDecimal.ZERO) // Original snapshot was 0 (free)
                .build();

        when(registrationRepository.findByUuidForUpdate(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.approveRegistration(organizer, registrationUuid);

        assertNotNull(dto);
        assertEquals("APPROVED", dto.getStatus());
        verify(paymentRepository).save(argThat(p -> p.getAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void initializeRegistration_rejectsMissingPackageExhibitionAndLockedExhibition() {
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUserForUpdate(companyUser)).thenReturn(company);

        ExhibitionPackage detached = ExhibitionPackage.builder()
                .id(10)
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();
        when(packageRepository.findById(10)).thenReturn(Optional.of(detached));
        assertEquals(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> registrationService.initializeRegistration(
                                companyUser.getId(), 10, null, null, null))
                        .getErrorCode());

        detached.setExhibition(Exhibition.builder().id(99).build());
        when(exhibitionRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> registrationService.initializeRegistration(
                                companyUser.getId(), 10, null, null, null))
                        .getErrorCode());
    }

    @Test
    void getRegistrationDetails_rejectsCompanyWithoutId() {
        UUID uuid = UUID.randomUUID();
        Company missingId = Company.builder().ownerUser(companyUser).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .uuid(uuid)
                .company(missingId)
                .build();
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));

        assertEquals(ErrorCode.REGISTRATION_COMPANY_MISSING,
                assertThrows(AppException.class,
                        () -> registrationService.getRegistrationDetails(uuid, companyUser.getId()))
                        .getErrorCode());
    }

    @Test
    void registrationSearchesCoverNullAndBlankKeywords() {
        Pageable pageable = PageRequest.of(0, 10);
        User organizer = User.builder().id(UUID.randomUUID()).build();
        when(registrationRepository.searchForOrganizer(
                organizer.getId(), null, null, null, pageable)).thenReturn(Page.empty(pageable));

        registrationService.getRegistrationsForOrganizer(organizer, null, null, null, pageable);

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.searchForExhibitor(
                company.getId(), null, null, pageable)).thenReturn(Page.empty(pageable));
        registrationService.getRegistrationsForExhibitor(companyUser, null, null, pageable);
        registrationService.getRegistrationsForExhibitor(companyUser, null, " ", pageable);
    }

    @Test
    void findRegistrationWithRelationsHandlesNullAndDelegates() {
        Assertions.assertTrue(registrationService.findRegistrationWithRelationsById(null).isEmpty());
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).build();
        when(registrationRepository.findByIdWithRelations(1)).thenReturn(Optional.of(registration));
        assertEquals(Optional.of(registration), registrationService.findRegistrationWithRelationsById(1));
    }

    @Test
    void approveRegistration_rejectsMissingOwnershipGraphAndClosedRegistration() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID uuid = UUID.randomUUID();

        ExhibitorRegistration missingPackage = ExhibitorRegistration.builder()
                .uuid(uuid).status(ExhibitorRegistrationStatus.PENDING).build();
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(missingPackage));
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class,
                        () -> registrationService.approveRegistration(organizer, uuid))
                        .getErrorCode());

        ExhibitorRegistration missingExhibition = ExhibitorRegistration.builder()
                .uuid(uuid)
                .exhibitionPackage(ExhibitionPackage.builder().build())
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(missingExhibition));
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class,
                        () -> registrationService.approveRegistration(organizer, uuid))
                        .getErrorCode());

        Exhibition exhibition = Exhibition.builder().build();
        ExhibitorRegistration missingOrganizer = ExhibitorRegistration.builder()
                .uuid(uuid)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(missingOrganizer));
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class,
                        () -> registrationService.approveRegistration(organizer, uuid))
                        .getErrorCode());

        exhibition.setOrganizer(User.builder().build());
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class,
                        () -> registrationService.approveRegistration(organizer, uuid))
                        .getErrorCode());

        exhibition.setOrganizer(organizer);
        exhibition.setStatus(ExhibitionStatus.REGISTRATION);
        exhibition.setStartDate(LocalDate.of(2026, Month.JANUARY, 12));
        assertEquals(ErrorCode.REGISTRATION_CLOSED,
                assertThrows(AppException.class,
                        () -> registrationService.approveRegistration(organizer, uuid))
                        .getErrorCode());
    }

    @Test
    void rejectRegistration_rejectsNullReasonAndCancelsAllPendingLinks() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID uuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().organizer(organizer).name("Expo").build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .id(10)
                .exhibition(exhibition)
                .template(PackageTemplate.builder().name("Package").build())
                .finalPrice(BigDecimal.TEN)
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(exhibitionPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));

        assertEquals(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED,
                assertThrows(AppException.class,
                        () -> registrationService.rejectRegistration(organizer, uuid, null))
                        .getErrorCode());

        Payment canceled = Payment.builder().orderCode(1L).status(PaymentStatus.PENDING).build();
        Payment withoutOrderCode = Payment.builder().status(PaymentStatus.PENDING).build();
        Payment cancelFailure = Payment.builder().orderCode(2L).status(PaymentStatus.PENDING).build();
        Payment alreadyFailed = Payment.builder().status(PaymentStatus.FAILED).build();
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(1))
                .thenReturn(List.of(canceled, withoutOrderCode, cancelFailure, alreadyFailed));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().doThrow(new RuntimeException("cancel failed"))
                .when(payOSIntegrationService).cancelPaymentLink(2L, "Organizer rejected registration");

        registrationService.rejectRegistration(organizer, uuid, "reason");

        assertEquals(PaymentStatus.FAILED, canceled.getStatus());
        assertEquals(PaymentStatus.FAILED, withoutOrderCode.getStatus());
        assertEquals(PaymentStatus.FAILED, cancelFailure.getStatus());
        verify(payOSIntegrationService).cancelPaymentLink(1L, "Organizer rejected registration");
    }

    @Test
    void cancelRegistration_handlesNullUserMissingRegistrationAndNoPayments() {
        UUID uuid = UUID.randomUUID();
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(AppException.class,
                        () -> registrationService.cancelRegistration(null, uuid))
                        .getErrorCode());

        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> registrationService.cancelRegistration(companyUser, uuid))
                        .getErrorCode());

        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(1)).thenReturn(List.of());

        ExhibitorRegistrationResponseDTO response = registrationService.cancelRegistration(companyUser, uuid);

        assertEquals("CANCELED", response.getStatus());
        Assertions.assertNull(response.getPaymentStatus());
    }

    @Test
    void cancelRegistration_handlesNullOrderCodeCancelFailureAndNonPendingPayment() {
        UUID uuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        Payment withoutOrderCode = Payment.builder().status(PaymentStatus.PENDING).build();
        Payment cancelFailure = Payment.builder().orderCode(2L).status(PaymentStatus.PENDING).build();
        Payment alreadyFailed = Payment.builder().status(PaymentStatus.FAILED).build();
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(1))
                .thenReturn(List.of(withoutOrderCode, cancelFailure, alreadyFailed));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().doThrow(new RuntimeException("cancel failed"))
                .when(payOSIntegrationService).cancelPaymentLink(2L, "Exhibitor canceled registration");

        registrationService.cancelRegistration(companyUser, uuid);

        assertEquals(PaymentStatus.FAILED, withoutOrderCode.getStatus());
        assertEquals(PaymentStatus.FAILED, cancelFailure.getStatus());
        verify(paymentRepository, never()).save(alreadyFailed);
    }

    @Test
    void getRegistrationDetails_keepsIndeterminateLinksPendingAndFailsMissingOrder() {
        UUID uuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
        Payment nonPaid = Payment.builder().orderCode(1L).status(PaymentStatus.PENDING).build();
        Payment missingLink = Payment.builder().orderCode(2L).status(PaymentStatus.PENDING).checkoutUrl("").build();
        Payment lookupFailure = Payment.builder().orderCode(3L).status(PaymentStatus.PENDING).build();
        Payment missingOrder = Payment.builder().status(PaymentStatus.PENDING).build();
        PaymentLink link = mock(PaymentLink.class);
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(nonPaid), Optional.of(missingLink),
                        Optional.of(lookupFailure), Optional.of(missingOrder));
        when(payOSIntegrationService.getPaymentLinkInformation(1L)).thenReturn(link);
        when(payOSIntegrationService.getPaymentLinkInformation(2L)).thenReturn(null);
        when(payOSIntegrationService.getPaymentLinkInformation(3L))
                .thenThrow(new RuntimeException("lookup failed"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.getRegistrationDetails(uuid, companyUser.getId());
        registrationService.getRegistrationDetails(uuid, companyUser.getId());
        registrationService.getRegistrationDetails(uuid, companyUser.getId());
        registrationService.getRegistrationDetails(uuid, companyUser.getId());

        assertEquals(PaymentStatus.PENDING, nonPaid.getStatus());
        assertEquals(PaymentStatus.PENDING, missingLink.getStatus());
        assertEquals(PaymentStatus.PENDING, lookupFailure.getStatus());
        assertEquals(PaymentStatus.FAILED, missingOrder.getStatus());
    }

    @Test
    void getRegistrationDetails_paidLinkAfterDeadlineDoesNotApprove() {
        UUID uuid = UUID.randomUUID();
        paidPackage.getExhibition().setStartDate(LocalDate.of(2026, Month.JANUARY, 12));
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        Payment payment = Payment.builder()
                .orderCode(5L)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("")
                .build();
        PaymentLink link = mock(PaymentLink.class);
        when(link.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(payment));
        when(payOSIntegrationService.getPaymentLinkInformation(5L)).thenReturn(link);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.getRegistrationDetails(uuid, companyUser.getId());

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT, registration.getStatus());
    }

    @Test
    void getRegistrationDetails_rejectsLinkGenerationAfterDeadline() {
        UUID uuid = UUID.randomUUID();
        paidPackage.getExhibition().setStartDate(LocalDate.of(2026, Month.JANUARY, 12));
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.empty());

        assertEquals(ErrorCode.REGISTRATION_CLOSED,
                assertThrows(AppException.class,
                        () -> registrationService.getRegistrationDetails(uuid, companyUser.getId()))
                        .getErrorCode());
    }

    @Test
    void getRegistrationDetails_recoversAllPaymentLinkCreationFailureOutcomes() {
        UUID uuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(uuid)
                .company(company)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .finalPriceSnapshot(BigDecimal.valueOf(123))
                .build();
        when(userService.getUserEntityById(companyUser.getId())).thenReturn(companyUser);
        when(companyService.getCompanyEntityForCurrentUser(companyUser)).thenReturn(company);
        when(registrationRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("create failed"));
        registrationService.getRegistrationDetails(uuid, companyUser.getId());
        registrationService.getRegistrationDetails(uuid, companyUser.getId());
        registrationService.getRegistrationDetails(uuid, companyUser.getId());

        verify(commissionCalculator, Mockito.times(3))
                .calculateCommission(eq(BigDecimal.valueOf(123)), any());
    }

    @Test
    void reviewMailHandlesMissingRecipientFallbackFieldsAndExecutorFailure() {
        ExhibitorRegistration missingCompany = ExhibitorRegistration.builder().id(1).build();
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                missingCompany, ExhibitorRegistrationStatus.REJECTED, null);

        Company missingOwner = Company.builder().id(UUID.randomUUID()).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1).company(missingOwner).build();
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.REJECTED, null);

        User missingEmail = User.builder().id(UUID.randomUUID()).build();
        missingOwner.setOwnerUser(missingEmail);
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.REJECTED, null);

        missingEmail.setEmail(" ");
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.REJECTED, null);

        missingEmail.setEmail("owner@example.com");
        missingEmail.setFullName("Owner");
        registration.setCurrencySnapshot("");
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.REJECTED, null);

        ExhibitionPackage packageWithoutTemplate = ExhibitionPackage.builder()
                .finalPrice(BigDecimal.TEN)
                .build();
        registration.setExhibitionPackage(packageWithoutTemplate);
        registration.setPackageNameSnapshot("");
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.PENDING_PAYMENT, null);

        Mockito.doThrow(new RuntimeException("executor failed"))
                .when(afterCommitExecutor).execute(any());
        ReflectionTestUtils.invokeMethod(registrationService,
                "sendExhibitorRegistrationReviewMailSafely",
                registration, ExhibitorRegistrationStatus.PENDING_PAYMENT, null);
    }

    @Test
    void dependencyValidationCoversAllMissingRelationsAndLazyFailure() {
        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND,
                assertThrows(AppException.class,
                        () -> ReflectionTestUtils.invokeMethod(
                                registrationService, "validateRegistrationDependencies", (Object) null))
                        .getErrorCode());

        User owner = User.builder().id(UUID.randomUUID()).build();
        Company validCompany = Company.builder().id(UUID.randomUUID()).ownerUser(owner).build();
        ExhibitionPackage validPackage = ExhibitionPackage.builder().id(1).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .uuid(UUID.randomUUID())
                .company(validCompany)
                .exhibitionPackage(validPackage)
                .build();

        registration.setCompany(null);
        assertDependencyInvalid(registration);
        registration.setCompany(Company.builder().ownerUser(owner).build());
        assertDependencyInvalid(registration);
        registration.setCompany(Company.builder().id(UUID.randomUUID()).build());
        assertDependencyInvalid(registration);
        registration.setCompany(Company.builder().id(UUID.randomUUID())
                .ownerUser(User.builder().build()).build());
        assertDependencyInvalid(registration);
        registration.setCompany(validCompany);
        registration.setExhibitionPackage(null);
        assertDependencyInvalid(registration);
        registration.setExhibitionPackage(ExhibitionPackage.builder().build());
        assertDependencyInvalid(registration);

        ExhibitorRegistration lazyFailure = mock(ExhibitorRegistration.class);
        when(lazyFailure.getCompany()).thenThrow(new jakarta.persistence.EntityNotFoundException());
        assertDependencyInvalid(lazyFailure);
    }

    private void assertDependencyInvalid(ExhibitorRegistration registration) {
        assertThrows(AppException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        registrationService, "validateRegistrationDependencies", registration));
    }

    @Test
    void mapToResponseHandlesCompanyWithoutOwner() {
        Company companyWithoutOwner = Company.builder()
                .id(UUID.randomUUID())
                .name("Company")
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .company(companyWithoutOwner)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();

        ExhibitorRegistrationResponseDTO response = ReflectionTestUtils.invokeMethod(
                registrationService, "mapToResponse", registration, null);

        Assertions.assertNull(response.getCompanyUserId());
        Assertions.assertNull(response.getCompanyEmail());
    }

    @Test
    void cancelRegistrationRejectsUserWithoutId() {
        User missingId = User.builder().build();
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(AppException.class,
                        () -> registrationService.cancelRegistration(missingId, UUID.randomUUID()))
                        .getErrorCode());
    }
}
