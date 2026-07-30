package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository.PaymentRoute;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.features.exhibition.services.impl.PayOSWebhookServiceImpl;
import com.example.vex360.features.wallet.services.PaymentRevenueRecognitionService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

@ExtendWith(MockitoExtension.class)
class PayOSWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private BoothProvisioningService boothProvisioningService;

    @Mock
    private PaymentFulfillmentService fulfillmentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PayOS payOS;

    @Mock
    private vn.payos.service.blocking.webhooks.WebhooksService webhookService;

    @Mock
    private PaymentRevenueRecognitionService revenueRecognitionService;

    @InjectMocks
    private PayOSWebhookServiceImpl webhookServiceWrapper;

    private Payment pendingPayment;
    private ExhibitorRegistration pendingRegistration;
    private WebhookData successWebhookData;
    private WebhookData failedWebhookData;

    @BeforeEach
    void setup() {
        pendingRegistration = ExhibitorRegistration.builder()
                .id(1)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .company(Company.builder().id(UUID.randomUUID())
                        .name("Test Co").build())
                .build();

        pendingPayment = Payment.builder()
                .id(100)
                .orderCode(123456L)
                .amount(BigDecimal.valueOf(1000000))
                .status(PaymentStatus.PENDING)
                .exhibitorRegistration(pendingRegistration)
                .build();

        successWebhookData = WebhookData.builder()
                .orderCode(123456L)
                .amount(1000000L)
                .description("Dang ky trien lam")
                .accountNumber("123456789")
                .reference("payos_ref_123")
                .transactionDateTime("2026-06-25T16:00:00")
                .currency("VND")
                .paymentLinkId("link_123")
                .code("00")
                .desc("Success")
                .counterAccountBankId("bank_abc")
                .counterAccountBankName("Bank ABC")
                .counterAccountName("Counter Party")
                .counterAccountNumber("987654321")
                .virtualAccountName("Virtual Account")
                .virtualAccountNumber("88888888")
                .build();

        failedWebhookData = WebhookData.builder()
                .orderCode(123456L)
                .amount(1000000L)
                .description("Dang ky trien lam")
                .accountNumber("123456789")
                .reference("payos_ref_123")
                .transactionDateTime("2026-06-25T16:00:00")
                .currency("VND")
                .paymentLinkId("link_123")
                .code("01")
                .desc("Failed")
                .counterAccountBankId("bank_abc")
                .counterAccountBankName("Bank ABC")
                .counterAccountName("Counter Party")
                .counterAccountNumber("987654321")
                .virtualAccountName("Virtual Account")
                .virtualAccountNumber("88888888")
                .build();
    }

    @Test
    void testHandleWebhook_Success_PaymentPaid() {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        when(boothProvisioningService.ensureBoothForApprovedRegistration(1))
                .thenReturn(Optional.of(Booth.builder().id(UUID.randomUUID()).build()));
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("00", result.getCode());
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("payos_ref_123", pendingPayment.getPaymentReference());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository).save(pendingRegistration);
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(1);
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void testHandleWebhook_Failure_PaymentFailed() {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(failedWebhookData);
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("01", result.getCode());
        assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testHandleWebhook_SignatureVerificationFailed_ThrowsAppException() {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenThrow(new RuntimeException("Invalid signature"));

        AppException exception = assertThrows(AppException.class, () -> {
            webhookServiceWrapper.handleWebhook(mockBody);
        });

        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
        verify(paymentRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testHandleWebhook_Success_PaymentPaid_ReferenceNull() {
        Object mockBody = new Object();
        WebhookData successDataNoRef = mock(WebhookData.class);
        when(successDataNoRef.getOrderCode()).thenReturn(123456L);
        when(successDataNoRef.getCode()).thenReturn("00");
        when(successDataNoRef.getCurrency()).thenReturn("VND");
        when(successDataNoRef.getAmount()).thenReturn(1000000L);
        when(successDataNoRef.getReference()).thenReturn(null);

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successDataNoRef);
        when(boothProvisioningService.ensureBoothForApprovedRegistration(1))
                .thenReturn(Optional.of(Booth.builder().id(UUID.randomUUID()).build()));
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("00", result.getCode());
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        Assertions.assertNull(pendingPayment.getPaymentReference());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository).save(pendingRegistration);
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void storagePackagePaymentPublishesCompletionEvent() {
        Object mockBody = new Object();
        Payment storagePayment = Payment.builder()
                .id(101)
                .orderCode(123456L)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .storagePackageOrderId(77)
                .amount(BigDecimal.valueOf(1000000))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(storagePayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        verify(eventPublisher).publishEvent(any(StoragePackagePaymentCompletedEvent.class));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void paidPayment_duplicateSuccessWebhook_repairsAndVerifiesBooth() {
        Object mockBody = new Object();
        pendingPayment.setStatus(PaymentStatus.PAID);
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.APPROVED);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        when(boothProvisioningService.ensureBoothForApprovedRegistration(1))
                .thenReturn(Optional.of(Booth.builder().id(UUID.randomUUID()).build()));
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(1);
    }

    @Test
    void handleWebhook_boothProvisioningFails_throwsAppException() {
        Object mockBody = new Object();
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.APPROVED);

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        when(boothProvisioningService.ensureBoothForApprovedRegistration(1)).thenReturn(Optional.empty());
        stubLockedPayment(pendingPayment);

        AppException ex = assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, ex.getErrorCode());
    }

    @Test
    void paidPayment_lateFailureWebhook_doesNotDowngradePayment() {
        Object mockBody = new Object();
        pendingPayment.setStatus(PaymentStatus.PAID);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(failedWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectedRegistration_successWebhook_recordsPaymentWithoutApproval() {
        Object mockBody = new Object();
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.REJECTED);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals(ExhibitorRegistrationStatus.REJECTED, pendingRegistration.getStatus());
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void paidStoragePayment_duplicateWebhook_doesNotIncreaseQuotaAgain() {
        Object mockBody = new Object();
        Payment storagePayment = Payment.builder()
                .orderCode(123456L)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .storagePackageOrderId(77)
                .amount(BigDecimal.valueOf(1000000))
                .currency("VND")
                .status(PaymentStatus.PAID)
                .build();
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(storagePayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void handleWebhook_amountSmallerThanInvoice_throwsAppException() {
        Object mockBody = new Object();
        WebhookData smallerAmountData = createWebhookData(123456L, 500000L, "VND", "00", "link_123", "payos_ref_123");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(smallerAmountData);
        stubLockedPayment(pendingPayment);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void handleWebhook_amountGreaterThanInvoice_throwsAppException() {
        Object mockBody = new Object();
        WebhookData largerAmountData = createWebhookData(123456L, 2000000L, "VND", "00", "link_123", "payos_ref_123");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(largerAmountData);
        stubLockedPayment(pendingPayment);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void handleWebhook_currencyMismatch_throwsAppException() {
        Object mockBody = new Object();
        WebhookData wrongCurrencyData = createWebhookData(123456L, 1000000L, "USD", "00", "link_123", "payos_ref_123");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(wrongCurrencyData);
        stubLockedPayment(pendingPayment);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void handleWebhook_paymentLinkMismatch_throwsAppException() {
        Object mockBody = new Object();
        pendingPayment.setCheckoutUrl("https://payos.vn/web/checkout_xyz123");
        WebhookData wrongLinkData = createWebhookData(123456L, 1000000L, "VND", "00", "different_link_456",
                "payos_ref_123");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(wrongLinkData);
        stubLockedPayment(pendingPayment);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void handleWebhook_duplicateProviderReference_throwsAppException() {
        Object mockBody = new Object();
        WebhookData duplicateRefData = createWebhookData(123456L, 1000000L, "VND", "00", "link_123",
                "payos_ref_already_used");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(duplicateRefData);
        stubLockedPayment(pendingPayment);
        when(paymentRepository.existsByPaymentReferenceAndIdNot("payos_ref_already_used", pendingPayment.getId()))
                .thenReturn(true);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));
        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void handleWebhook_unknownOrderCode_returnsDataWithoutFulfilling() {
        Object mockBody = new Object();
        WebhookData unknownOrderData = createWebhookData(999999L, 1000000L, "VND", "00", "link_999", "payos_ref_999");

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(unknownOrderData);
        when(paymentRepository.findRouteByOrderCode(999999L)).thenReturn(Optional.empty());

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals(999999L, result.getOrderCode());
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private WebhookData createWebhookData(Long orderCode, Long amount, String currency, String code,
            String paymentLinkId, String reference) {
        return WebhookData.builder()
                .orderCode(orderCode != null ? orderCode : 123456L)
                .amount(amount != null ? amount : 1000000L)
                .description("Dang ky trien lam")
                .accountNumber("123456789")
                .reference(reference != null ? reference : "payos_ref_123")
                .transactionDateTime("2026-06-25T16:00:00")
                .currency(currency != null ? currency : "VND")
                .paymentLinkId(paymentLinkId != null ? paymentLinkId : "link_123")
                .code(code != null ? code : "00")
                .desc("Success")
                .build();
    }

    private void stubLockedPayment(Payment payment) {
        Integer registrationId = payment.getExhibitorRegistration() == null
                ? null
                : payment.getExhibitorRegistration().getId();
        PaymentRoute route = new PaymentRoute() {
            @Override
            public PaymentType getPaymentType() {
                return payment.getPaymentType();
            }

            @Override
            public Integer getRegistrationId() {
                return registrationId;
            }
        };
        when(paymentRepository.findRouteByOrderCode(payment.getOrderCode())).thenReturn(Optional.of(route));
        when(paymentRepository.findByOrderCodeForUpdate(payment.getOrderCode())).thenReturn(Optional.of(payment));
        if (registrationId != null) {
            when(registrationRepository.findByIdForUpdate(registrationId))
                    .thenReturn(Optional.of(payment.getExhibitorRegistration()));
        }
    }
}
