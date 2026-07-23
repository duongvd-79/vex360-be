package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository.PaymentRoute;
import com.example.vex360.features.exhibition.services.impl.PayOSWebhookServiceImpl;
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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PayOS payOS;

    @Mock
    private vn.payos.service.blocking.webhooks.WebhooksService webhookService;

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
    void testHandleWebhook_Success_PaymentPaid() throws Exception {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("00", result.getCode());
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("payos_ref_123", pendingPayment.getPaymentReference());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository).save(pendingRegistration);
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void testHandleWebhook_Failure_PaymentFailed() throws Exception {
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
    void testHandleWebhook_SignatureVerificationFailed_ThrowsAppException() throws Exception {
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
    void testHandleWebhook_Success_PaymentPaid_ReferenceNull() throws Exception {
        Object mockBody = new Object();
        WebhookData successDataNoRef = Mockito.mock(WebhookData.class);
        when(successDataNoRef.getOrderCode()).thenReturn(123456L);
        when(successDataNoRef.getCode()).thenReturn("00");
        when(successDataNoRef.getReference()).thenReturn(null);

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successDataNoRef);
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
    void storagePackagePaymentPublishesCompletionEvent() throws Exception {
        Object mockBody = new Object();
        Payment storagePayment = Payment.builder()
                .orderCode(123456L)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .storagePackageOrderId(77)
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
    void paidPayment_duplicateSuccessWebhook_doesNotPublishAgain() throws Exception {
        Object mockBody = new Object();
        pendingPayment.setStatus(PaymentStatus.PAID);
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.APPROVED);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void paidPayment_lateFailureWebhook_doesNotDowngradePayment() throws Exception {
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
    void rejectedRegistration_successWebhook_recordsPaymentWithoutApproval() throws Exception {
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
    void paidStoragePayment_duplicateWebhook_doesNotIncreaseQuotaAgain() throws Exception {
        Object mockBody = new Object();
        Payment storagePayment = Payment.builder()
                .orderCode(123456L)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .storagePackageOrderId(77)
                .status(PaymentStatus.PAID)
                .build();
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(storagePayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
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
