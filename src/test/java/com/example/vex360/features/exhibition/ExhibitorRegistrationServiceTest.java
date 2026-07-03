package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.PaymentStatus;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.impl.ExhibitorRegistrationServiceImpl;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;

@ExtendWith(MockitoExtension.class)
class ExhibitorRegistrationServiceTest {

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private ExhibitionPackageRepository packageRepository;

    @Mock
    private UserService userService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PayOSIntegrationService payOSIntegrationService;

    @Mock
    private BoothProvisioningService boothProvisioningService;

    @InjectMocks
    private ExhibitorRegistrationServiceImpl registrationService;

    private User companyUser;
    private ExhibitionPackage paidPackage;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(registrationService, "returnUrl", "http://localhost:5173/payment/success");
        ReflectionTestUtils.setField(registrationService, "cancelUrl", "http://localhost:5173/payment/cancel");

        companyUser = User.builder()
                .id(UUID.randomUUID())
                .email("company@example.com")
                .fullName("Test Company")
                .build();

        PackageTemplate template = PackageTemplate.builder()
                .id(UUID.randomUUID())
                .name("Standard Package")
                .price(BigDecimal.valueOf(1000000))
                .build();

        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .name("Expo")
                .build();

        paidPackage = ExhibitionPackage.builder()
                .id(10)
                .template(template)
                .exhibition(exhibition)
                .finalPrice(BigDecimal.valueOf(1500000)) // finalPrice >= floorPrice (1M)
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
        when(packageRepository.findById(10)).thenReturn(Optional.of(paidPackage));
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistration registration = registrationService.initializeRegistration(companyUser.getId(), 10);

        assertNotNull(registration);
        assertEquals(ExhibitorRegistrationStatus.PENDING, registration.getStatus());
        assertEquals(companyUser, registration.getCompany());
        assertEquals(paidPackage, registration.getExhibitionPackage());

        verify(userService).getUserEntityById(companyUser.getId());
        verify(packageRepository).findById(10);
        verify(registrationRepository).save(any(ExhibitorRegistration.class));
    }

    @Test
    void testInitializeRegistration_PackageNotFound_ThrowsException() {
        when(userService.getUserEntityById(any(UUID.class))).thenReturn(companyUser);
        when(packageRepository.findById(999)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.initializeRegistration(companyUser.getId(), 999);
        });

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_NOT_FOUND, exception.getErrorCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void testGetRegistrationDetails_RegistrationNotFound_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());
        });

        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_UnauthorizedUser_ThrowsException() {
        UUID registrationUuid = UUID.randomUUID();
        User anotherCompany = User.builder().id(UUID.randomUUID()).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .company(anotherCompany)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());
        });

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_Success() throws Exception {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("chkUrl", dto.getCheckoutUrl());
        assertEquals("PENDING", dto.getPaymentStatus());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_FailedPayment() throws Exception {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment failedPayment = Payment.builder()
                .id(100)
                .status(PaymentStatus.FAILED)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(failedPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("chkUrl", dto.getCheckoutUrl());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_LongDescription() throws Exception {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitionPackage longPackage = ExhibitorRegistrationServiceTest.this.paidPackage;
        longPackage.getExhibition().setName("Tech Exhibition Show Expo Event 2026 Very Long Name");
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(longPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentLinkResponse payOSResponse = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);
        when(payOSResponse.getCheckoutUrl()).thenReturn("chkUrl");
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenReturn(payOSResponse);

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_GeneratePayOSLink_ThrowsException() throws Exception {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("PayOS Link Generation Failed"));

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        org.junit.jupiter.api.Assertions.assertNull(dto.getCheckoutUrl());
    }

    @Test
    void testGetRegistrationsForOrganizer_Unauthenticated_ThrowsException() {
        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForOrganizer(null, UUID.randomUUID(),
                    ExhibitorRegistrationStatus.PENDING, "", org.springframework.data.domain.Pageable.unpaged());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        User userNoId = User.builder().id(null).build();
        AppException exception2 = assertThrows(AppException.class, () -> {
            registrationService.getRegistrationsForOrganizer(userNoId, UUID.randomUUID(),
                    ExhibitorRegistrationStatus.PENDING, "", org.springframework.data.domain.Pageable.unpaged());
        });
        assertEquals(ErrorCode.UNAUTHENTICATED, exception2.getErrorCode());
    }

    @Test
    void testGetRegistrationsForOrganizer_EmptyList_ReturnsPage() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);
        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(), pageRequest, 0));

        var pageResponse = registrationService.getRegistrationsForOrganizer(organizer, exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest);

        assertNotNull(pageResponse);
        assertEquals(0, pageResponse.getContent().size());
        verify(paymentRepository, never()).findByExhibitorRegistrationIdIn(any());
    }

    @Test
    void testGetRegistrationsForOrganizer_WithPayments_ReturnsPage() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);
        ExhibitorRegistration reg1 = ExhibitorRegistration.builder().id(1).uuid(UUID.randomUUID()).company(companyUser)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();
        ExhibitorRegistration reg2 = ExhibitorRegistration.builder().id(2).uuid(UUID.randomUUID()).company(companyUser)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(
                        new org.springframework.data.domain.PageImpl<>(java.util.List.of(reg1, reg2), pageRequest, 2));

        Payment payment1Old = Payment.builder().id(100).status(PaymentStatus.FAILED).exhibitorRegistration(reg1)
                .createdAt(java.time.LocalDateTime.now().minusDays(1)).build();
        Payment payment1New = Payment.builder().id(101).status(PaymentStatus.PENDING).exhibitorRegistration(reg1)
                .createdAt(java.time.LocalDateTime.now()).build();
        Payment payment2 = Payment.builder().id(102).status(PaymentStatus.PAID).exhibitorRegistration(reg2)
                .createdAt(java.time.LocalDateTime.now()).build();

        when(paymentRepository.findByExhibitorRegistrationIdIn(java.util.List.of(1, 2)))
                .thenReturn(java.util.List.of(payment1Old, payment1New, payment2));

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
        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.empty());

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

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, registrationUuid);
        });
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_NotPending_ThrowsException() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).status(ExhibitorRegistrationStatus.APPROVED).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.approveRegistration(organizer, registrationUuid);
        });
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void testApproveRegistration_PaidPackage_StatusSetToPendingPayment() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.TEN).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(companyUser).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.approveRegistration(organizer, registrationUuid);

        assertNotNull(dto);
        assertEquals("PENDING_PAYMENT", dto.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testApproveRegistration_FreePackage_StatusSetToApproved_CreateFreePayment() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID registrationUuid = UUID.randomUUID();
        Exhibition exhibition = Exhibition.builder().id(1).organizer(organizer).name("Expo").build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Std").build();
        ExhibitionPackage ep = ExhibitionPackage.builder().id(10).exhibition(exhibition).template(template)
                .finalPrice(BigDecimal.ZERO).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(1).uuid(registrationUuid)
                .exhibitionPackage(ep).company(companyUser).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.approveRegistration(organizer, registrationUuid);

        assertNotNull(dto);
        assertEquals("APPROVED", dto.getStatus());
        verify(paymentRepository).save(any(Payment.class));
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
                .exhibitionPackage(ep).company(companyUser).status(ExhibitorRegistrationStatus.PENDING_PAYMENT).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(ExhibitorRegistration.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = registrationService.rejectRegistration(organizer, registrationUuid, "Invalid docs");

        assertNotNull(dto);
        assertEquals("REJECTED", dto.getStatus());
        assertEquals("Invalid docs", dto.getRejectedReason());
    }

    @Test
    void testGetRegistrationDetails_StatusNotPendingPayment_DoesNotGeneratePaymentLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING)
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1)).thenReturn(Optional.empty());

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        org.junit.jupiter.api.Assertions.assertNull(dto.getCheckoutUrl());
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void testGetRegistrationDetails_PendingPayment_PaymentPending_DoesNotGeneratePaymentLink() {
        UUID registrationUuid = UUID.randomUUID();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(registrationUuid)
                .company(companyUser)
                .exhibitionPackage(paidPackage)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .build();

        Payment pendingPayment = Payment.builder()
                .id(100)
                .status(PaymentStatus.PENDING)
                .checkoutUrl("oldCheckoutUrl")
                .build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(pendingPayment));

        var dto = registrationService.getRegistrationDetails(registrationUuid, companyUser.getId());

        assertNotNull(dto);
        assertEquals("oldCheckoutUrl", dto.getCheckoutUrl());
        verify(payOSIntegrationService, never()).createPaymentLink(any(), any(), any(), any(), any());
    }

    @Test
    void testGetRegistrationsForOrganizer_OlderPaymentSkipped() {
        User organizer = User.builder().id(UUID.randomUUID()).build();
        UUID exhibitionUuid = UUID.randomUUID();
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);
        ExhibitorRegistration reg1 = ExhibitorRegistration.builder().id(1).uuid(UUID.randomUUID()).company(companyUser)
                .exhibitionPackage(paidPackage).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.searchForOrganizer(organizer.getId(), exhibitionUuid,
                ExhibitorRegistrationStatus.PENDING, "pro", pageRequest))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(reg1), pageRequest, 1));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Payment payment1New = Payment.builder().id(101).status(PaymentStatus.PENDING).exhibitorRegistration(reg1)
                .createdAt(now).build();
        Payment payment1Old = Payment.builder().id(100).status(PaymentStatus.FAILED).exhibitorRegistration(reg1)
                .createdAt(now.minusDays(1)).build();

        when(paymentRepository.findByExhibitorRegistrationIdIn(java.util.List.of(1)))
                .thenReturn(java.util.List.of(payment1New, payment1Old));

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
                .exhibitionPackage(ep).company(companyUser).status(ExhibitorRegistrationStatus.APPROVED).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(organizer, registrationUuid, "Invalid docs");
        });
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
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
                .exhibitionPackage(ep).company(companyUser).status(ExhibitorRegistrationStatus.PENDING).build();

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));
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
        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.empty());

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

        when(registrationRepository.findByUuid(registrationUuid)).thenReturn(Optional.of(registration));

        AppException exception = assertThrows(AppException.class, () -> {
            registrationService.rejectRegistration(organizer, registrationUuid, "reason");
        });
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }
}
