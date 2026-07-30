package com.example.vex360.features.wallet;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.wallet.entities.OrganizerWalletTransaction;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.features.wallet.services.PaymentRevenueRecognitionService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;

@ExtendWith(MockitoExtension.class)
class PaymentRevenueRecognitionServiceTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private OrganizerWalletDomainService organizerWalletDomainService;

    @InjectMocks
    private PaymentRevenueRecognitionService recognitionService;

    private User organizerUser;
    private Company company;
    private Exhibition exhibition;
    private ExhibitionPackage exhibitionPackage;
    private ExhibitorRegistration registration;
    private Payment payment;

    @BeforeEach
    void setUp() {
        organizerUser = User.builder().id(UUID.randomUUID()).email("organizer@test.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(organizerUser).name("Org Company").build();
        exhibition = Exhibition.builder().id(10).organizer(organizerUser).status(ExhibitionStatus.ACTIVE).build();
        exhibitionPackage = ExhibitionPackage.builder().id(5).exhibition(exhibition).build();
        registration = ExhibitorRegistration.builder().id(1).exhibitionPackage(exhibitionPackage).build();
        payment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .paymentType(PaymentType.EXHIBITION_REGISTRATION)
                .status(PaymentStatus.PAID)
                .amount(new BigDecimal("1000000.00"))
                .organizerPayout(new BigDecimal("1000000.00"))
                .exhibitorRegistration(registration)
                .build();
    }

    @Test
    void recognizeRevenue_ActiveExhibition_CreditsPendingOnly() {
        when(companyService.getCompanyEntityForCurrentUser(organizerUser)).thenReturn(company);
        OrganizerWalletTransaction mockTx = OrganizerWalletTransaction.builder().id(1L).build();
        when(organizerWalletDomainService.creditPendingPayment(eq(company), eq(exhibition), eq(payment),
                eq(new BigDecimal("1000000.00")), any(), any()))
                .thenReturn(mockTx);

        OrganizerWalletTransaction result = recognitionService.recognizeRevenueForPayment(payment);

        assertNotNull(result);
        verify(organizerWalletDomainService).creditPendingPayment(eq(company), eq(exhibition), eq(payment),
                eq(new BigDecimal("1000000.00")), any(), any());
        verify(organizerWalletDomainService, never()).releasePendingRevenue(any(), any(), any(), any(), any());
    }

    @Test
    void recognizeRevenue_CompletedExhibition_CreditsPendingAndReleasesImmediately() {
        exhibition.setStatus(ExhibitionStatus.COMPLETED);
        when(companyService.getCompanyEntityForCurrentUser(organizerUser)).thenReturn(company);
        OrganizerWalletTransaction mockTx = OrganizerWalletTransaction.builder().id(1L).build();
        when(organizerWalletDomainService.creditPendingPayment(eq(company), eq(exhibition), eq(payment),
                eq(new BigDecimal("1000000.00")), any(), any()))
                .thenReturn(mockTx);

        OrganizerWalletTransaction result = recognitionService.recognizeRevenueForPayment(payment);

        assertNotNull(result);
        verify(organizerWalletDomainService).creditPendingPayment(eq(company), eq(exhibition), eq(payment),
                eq(new BigDecimal("1000000.00")), any(), any());
        verify(organizerWalletDomainService).releasePendingRevenue(eq(company), eq(exhibition),
                eq(new BigDecimal("1000000.00")), any(), any());
    }

    @Test
    void recognizeRevenue_StoragePackagePayment_SkipsWalletCredit() {
        payment.setPaymentType(PaymentType.STORAGE_PACKAGE);

        OrganizerWalletTransaction result = recognitionService.recognizeRevenueForPayment(payment);

        assertNull(result);
        verify(organizerWalletDomainService, never()).creditPendingPayment(any(), any(), any(), any(), any(), any());
    }
}
