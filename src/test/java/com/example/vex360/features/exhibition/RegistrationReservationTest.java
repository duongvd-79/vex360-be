package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.ExhibitorRegistrationResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.jobs.RegistrationReservationExpiryJob;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.features.exhibition.services.ExhibitorRegistrationService;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.mail.AfterCommitExecutor;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RegistrationReservationTest {

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExhibitionRepository exhibitionRepository;
    @Mock
    private ExhibitionPackageRepository packageRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private PayOSIntegrationService payOSIntegrationService;
    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;
    @Mock
    private ExhibitionParticipationPolicy participationPolicy;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MailService mailService;
    @Spy
    private AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();

    @InjectMocks
    private ExhibitorRegistrationService registrationService;

    private User organizer;
    private Exhibition exhibition;
    private ExhibitionPackage exhibitionPackage;
    private ExhibitorRegistration registration;
    private Company company;

    @BeforeEach
    void setUp() {
        organizer = User.builder().id(UUID.randomUUID()).email("organizer@vex360.com").fullName("Organizer").build();
        exhibition = Exhibition.builder().id(10).name("Tech Expo").organizer(organizer).build();
        PackageTemplate template = PackageTemplate.builder().id(UUID.randomUUID()).name("Gold Tier").build();
        exhibitionPackage = ExhibitionPackage.builder()
                .id(100)
                .exhibition(exhibition)
                .template(template)
                .finalPrice(new BigDecimal("500.00"))
                .maxBooths(2)
                .build();

        company = Company.builder().id(UUID.randomUUID()).name("Test Company").ownerUser(organizer).build();

        registration = ExhibitorRegistration.builder()
                .id(1)
                .uuid(UUID.randomUUID())
                .exhibitionPackage(exhibitionPackage)
                .company(company)
                .status(ExhibitorRegistrationStatus.PENDING)
                .submittedAt(Instant.now())
                .boothName("Test Booth")
                .boothDescription("Test Description")
                .build();
    }

    @Test
    void approveRegistration_whenPackageFull_throwsExhibitionPackageFull() {
        UUID regUuid = registration.getUuid();
        when(registrationRepository.findByUuidForUpdate(regUuid)).thenReturn(Optional.of(registration));
        when(timelinePolicy.isRegistrationProcessingOpen(exhibition)).thenReturn(true);
        when(registrationRepository.countActiveAndReservedByPackageId(eq(100), any(Instant.class))).thenReturn(2L);

        AppException exception = assertThrows(AppException.class,
                () -> registrationService.approveRegistration(organizer, regUuid));

        assertEquals(ErrorCode.EXHIBITION_PACKAGE_FULL, exception.getErrorCode());
    }

    @Test
    void approveRegistration_whenPaidPackage_setsPendingPaymentAnd24hReservedUntil() {
        UUID regUuid = registration.getUuid();
        when(registrationRepository.findByUuidForUpdate(regUuid)).thenReturn(Optional.of(registration));
        when(timelinePolicy.isRegistrationProcessingOpen(exhibition)).thenReturn(true);
        when(registrationRepository.countActiveAndReservedByPackageId(eq(100), any(Instant.class))).thenReturn(1L);
        when(registrationRepository.save(any(ExhibitorRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExhibitorRegistrationResponseDTO dto = registrationService.approveRegistration(organizer, regUuid);

        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT.name(), dto.getStatus());
        assertNotNull(dto.getReservedUntil());
        Instant expectedMin = Instant.now().plus(23, ChronoUnit.HOURS);
        Instant expectedMax = Instant.now().plus(25, ChronoUnit.HOURS);
        assertEquals(true, dto.getReservedUntil().isAfter(expectedMin) && dto.getReservedUntil().isBefore(expectedMax));
    }

    @Test
    void cleanupExpiredReservations_expiresOverdueRegistrations() {
        RegistrationReservationExpiryJob job = new RegistrationReservationExpiryJob(
                registrationRepository, paymentRepository, payOSIntegrationService);

        ExhibitorRegistration expiredReg = ExhibitorRegistration.builder()
                .id(99)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .reservedUntil(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        Payment pendingPayment = Payment.builder()
                .id(Integer.valueOf(999))
                .orderCode(123456L)
                .status(PaymentStatus.PENDING)
                .build();

        when(registrationRepository.findExpiredPendingRegistrations(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(expiredReg));
        when(paymentRepository.findByExhibitorRegistrationIdForUpdate(Integer.valueOf(99)))
                .thenReturn(List.of(pendingPayment));

        job.cleanupExpiredReservations();

        assertEquals(ExhibitorRegistrationStatus.EXPIRED, expiredReg.getStatus());
        assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
        verify(payOSIntegrationService).cancelPaymentLink(123456L, "Reservation expired after 24h");
    }
}
