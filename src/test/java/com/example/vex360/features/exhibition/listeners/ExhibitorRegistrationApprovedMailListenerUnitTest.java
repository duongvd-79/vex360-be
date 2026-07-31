package com.example.vex360.features.exhibition.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.mail.MailService;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class ExhibitorRegistrationApprovedMailListenerUnitTest {

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MailService mailService;

    @InjectMocks
    private ExhibitorRegistrationApprovedMailListener listener;

    private User owner;
    private Company company;
    private Exhibition exhibition;
    private PackageTemplate packageTemplate;
    private ExhibitionPackage exhibitionPackage;
    private ExhibitorRegistration registration;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Company Owner")
                .build();

        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Tech Corp")
                .ownerUser(owner)
                .build();

        exhibition = Exhibition.builder()
                .id(10)
                .name("Tech Expo 2026")
                .build();

        packageTemplate = PackageTemplate.builder()
                .id(UUID.randomUUID())
                .name("Standard Package")
                .build();

        exhibitionPackage = ExhibitionPackage.builder()
                .id(1)
                .template(packageTemplate)
                .finalPrice(BigDecimal.valueOf(5000000))
                .exhibition(exhibition)
                .build();

        registration = ExhibitorRegistration.builder()
                .id(100)
                .company(company)
                .exhibitionPackage(exhibitionPackage)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .build();
    }

    @Test
    void handleExhibitorRegistrationApproved_FreePayment_SendsApprovalEmail() {
        Payment freePayment = Payment.builder()
                .id(1)
                .paymentProvider("FREE")
                .amount(BigDecimal.ZERO)
                .status(PaymentStatus.PAID)
                .build();

        when(registrationRepository.findById(100)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(100))
                .thenReturn(Optional.of(freePayment));

        ExhibitorRegistrationApprovedEvent event = new ExhibitorRegistrationApprovedEvent(this, registration);
        listener.handleExhibitorRegistrationApproved(event);

        verify(mailService).sendExhibitorRegistrationReviewResultEmail(
                eq("owner@example.com"),
                eq("Company Owner"),
                eq("Tech Corp"),
                eq("Tech Expo 2026"),
                eq("Standard Package"),
                eq(BigDecimal.ZERO),
                eq("VND"),
                eq(ExhibitorRegistrationStatus.APPROVED),
                isNull());
    }

    @Test
    void handleExhibitorRegistrationApproved_PaidPayment_SendsPaymentConfirmedEmail() {
        Instant paidAt = Instant.now();
        registration.setPackageNameSnapshot("Snapshot Package");
        registration.setCurrencySnapshot("VND");
        Payment paidPayment = Payment.builder()
                .id(2)
                .paymentProvider("PAYOS")
                .amount(BigDecimal.valueOf(5000000))
                .currency("USD")
                .orderCode(987654L)
                .status(PaymentStatus.PAID)
                .paidAt(paidAt)
                .build();

        when(registrationRepository.findById(100)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(100))
                .thenReturn(Optional.of(paidPayment));

        ExhibitorRegistrationApprovedEvent event = new ExhibitorRegistrationApprovedEvent(this, registration);
        listener.handleExhibitorRegistrationApproved(event);

        verify(mailService).sendExhibitorRegistrationPaymentConfirmedEmail(
                eq("owner@example.com"),
                eq("Company Owner"),
                eq("Tech Corp"),
                eq("Tech Expo 2026"),
                eq("Snapshot Package"),
                eq(BigDecimal.valueOf(5000000)),
                eq("USD"),
                eq(987654L),
                eq(paidAt));
    }

    @Test
    void handleExhibitorRegistrationApproved_MissingPayment_FallbackToApprovedEmail() {
        when(registrationRepository.findById(100)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(100))
                .thenReturn(Optional.empty());

        ExhibitorRegistrationApprovedEvent event = new ExhibitorRegistrationApprovedEvent(this, registration);
        listener.handleExhibitorRegistrationApproved(event);

        verify(mailService).sendExhibitorRegistrationReviewResultEmail(
                eq("owner@example.com"),
                eq("Company Owner"),
                eq("Tech Corp"),
                eq("Tech Expo 2026"),
                eq("Standard Package"),
                eq(BigDecimal.valueOf(5000000)),
                eq("VND"),
                eq(ExhibitorRegistrationStatus.APPROVED),
                isNull());
    }

    @Test
    void handleExhibitorRegistrationApproved_MissingPrice_UsesGenericApprovalData() {
        registration.setExhibitionPackage(null);
        when(registrationRepository.findById(100)).thenReturn(Optional.of(registration));
        when(paymentRepository.findFirstByExhibitorRegistrationIdOrderByCreatedAtDesc(100))
                .thenReturn(Optional.empty());

        listener.handleExhibitorRegistrationApproved(
                new ExhibitorRegistrationApprovedEvent(this, registration));

        verify(mailService).sendExhibitorRegistrationReviewResultEmail(
                eq("owner@example.com"),
                eq("Company Owner"),
                eq("Tech Corp"),
                eq(""),
                eq(""),
                isNull(),
                eq("VND"),
                eq(ExhibitorRegistrationStatus.APPROVED),
                isNull());
    }

    @Test
    void handleExhibitorRegistrationApproved_MissingRecipient_DoesNotSend() {
        company.setOwnerUser(null);
        when(registrationRepository.findById(100)).thenReturn(Optional.of(registration));

        ExhibitorRegistrationApprovedEvent event = new ExhibitorRegistrationApprovedEvent(this, registration);
        listener.handleExhibitorRegistrationApproved(event);

        verify(mailService, never()).sendExhibitorRegistrationReviewResultEmail(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyString());
        verify(mailService, never()).sendExhibitorRegistrationPaymentConfirmedEmail(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), any());
    }
}
