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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.impl.PayOSWebhookServiceImpl;
import com.example.vex360.shared.entities.ExhibitorRegistration;
import com.example.vex360.shared.entities.Payment;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

@ExtendWith(MockitoExtension.class)
public class PayOSWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private BoothProvisioningService boothProvisioningService;

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
    public void setup() {
        pendingRegistration = ExhibitorRegistration.builder()
                .id(1)
                .status(ExhibitorRegistrationStatus.PENDING)
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
    public void testHandleWebhook_Success_PaymentPaid() throws Exception {
        Object mockBody = new Object();
        
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        when(paymentRepository.findByOrderCode(123456L)).thenReturn(Optional.of(pendingPayment));

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("00", result.getCode());
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("payos_ref_123", pendingPayment.getPaymentReference());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository).save(pendingRegistration);
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(pendingRegistration);
    }

    @Test
    public void testHandleWebhook_Failure_PaymentFailed() throws Exception {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(failedWebhookData);
        when(paymentRepository.findByOrderCode(123456L)).thenReturn(Optional.of(pendingPayment));

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("01", result.getCode());
        assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
        assertEquals(ExhibitorRegistrationStatus.PENDING, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository, never()).save(any());
        verify(boothProvisioningService, never()).ensureBoothForApprovedRegistration(any());
    }

    @Test
    public void testHandleWebhook_SignatureVerificationFailed_ThrowsAppException() throws Exception {
        Object mockBody = new Object();

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenThrow(new RuntimeException("Invalid signature"));

        AppException exception = assertThrows(AppException.class, () -> {
            webhookServiceWrapper.handleWebhook(mockBody);
        });

        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
        verify(paymentRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(boothProvisioningService, never()).ensureBoothForApprovedRegistration(any());
    }
}
