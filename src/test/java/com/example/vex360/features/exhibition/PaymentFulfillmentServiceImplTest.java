package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentReceiptRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.impl.PaymentFulfillmentServiceImpl;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentReceiptStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.model.webhooks.WebhookData;

@ExtendWith(MockitoExtension.class)
class PaymentFulfillmentServiceImplTest {

    @Mock
    private PaymentReceiptRepository receiptRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private BoothProvisioningService boothProvisioningService;

    @InjectMocks
    private PaymentFulfillmentServiceImpl fulfillmentService;

    private Long orderCode;

    @BeforeEach
    void setup() {
        orderCode = 999111L;
    }

    @Test
    void recordReceipt_createsNewPendingReceipt() {
        WebhookData data = WebhookData.builder()
                .orderCode(orderCode)
                .amount(100000L)
                .description("Test receipt")
                .desc("Test receipt")
                .accountNumber("123456")
                .reference("ref_999")
                .transactionDateTime("2026-07-28 12:00:00")
                .currency("VND")
                .paymentLinkId("link_123")
                .code("00")
                .build();

        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.empty());
        when(receiptRepository.save(any(PaymentReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentReceipt receipt = fulfillmentService.recordReceipt(data);

        assertNotNull(receipt);
        assertEquals(orderCode, receipt.getOrderCode());
        assertEquals(PaymentReceiptStatus.PENDING, receipt.getStatus());
        assertEquals("ref_999", receipt.getPaymentReference());
        verify(receiptRepository).save(any(PaymentReceipt.class));
    }

    @Test
    void recordReceipt_returnsExistingReceipt() {
        WebhookData data = WebhookData.builder()
                .orderCode(orderCode)
                .amount(100000L)
                .description("Test receipt")
                .desc("Test receipt")
                .accountNumber("123456")
                .reference("ref_999")
                .transactionDateTime("2026-07-28 12:00:00")
                .currency("VND")
                .paymentLinkId("link_123")
                .code("00")
                .build();
        PaymentReceipt existing = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.SUCCEEDED).build();

        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(existing));

        PaymentReceipt result = fulfillmentService.recordReceipt(data);

        assertEquals(existing, result);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void updateReceiptSucceeded_setsSucceededStatusAndBoothId() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING).build();
        UUID boothId = UUID.randomUUID();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptSucceeded(orderCode, 5, boothId);

        assertEquals(PaymentReceiptStatus.SUCCEEDED, receipt.getStatus());
        assertEquals(5, receipt.getRegistrationId());
        assertEquals(boothId, receipt.getBoothId());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void updateReceiptFailed_setsRetryableFailedWithBackoff() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING).retryCount(0).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptFailed(orderCode, new RuntimeException("DB Connection Timeout"));

        assertEquals(PaymentReceiptStatus.RETRYABLE_FAILED, receipt.getStatus());
        assertEquals(1, receipt.getRetryCount());
        assertNotNull(receipt.getNextRetryAt());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void updateReceiptFailed_dependencyInvalid_movesToManualReview() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING).retryCount(0).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptFailed(orderCode, new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID));

        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());
        assertEquals(1, receipt.getRetryCount());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void processFulfillmentForOrderCode_provisionsBoothAndUpdatesReceipt() {
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(5).status(ExhibitorRegistrationStatus.APPROVED).build();
        Payment payment = Payment.builder().orderCode(orderCode).status(PaymentStatus.PAID).paymentType(PaymentType.EXHIBITION_REGISTRATION).exhibitorRegistration(reg).build();
        Booth booth = Booth.builder().id(UUID.randomUUID()).build();
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.SUCCEEDED).boothId(booth.getId()).build();

        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(reg));
        when(boothProvisioningService.ensureBoothForApprovedRegistration(5)).thenReturn(Optional.of(booth));
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(receipt));

        Optional<PaymentReceipt> res = fulfillmentService.processFulfillmentForOrderCode(orderCode);

        assertTrue(res.isPresent());
        assertEquals(PaymentReceiptStatus.SUCCEEDED, res.get().getStatus());
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(5);
    }

    @Test
    void replayFulfillment_resetsManualReviewAndFulfills() {
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(5).status(ExhibitorRegistrationStatus.APPROVED).build();
        Payment payment = Payment.builder().orderCode(orderCode).status(PaymentStatus.PAID).paymentType(PaymentType.EXHIBITION_REGISTRATION).exhibitorRegistration(reg).build();
        Booth booth = Booth.builder().id(UUID.randomUUID()).build();
        PaymentReceipt manualReceipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.MANUAL_REVIEW).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(manualReceipt));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(reg));
        when(boothProvisioningService.ensureBoothForApprovedRegistration(5)).thenReturn(Optional.of(booth));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(manualReceipt));

        Optional<PaymentReceipt> res = fulfillmentService.replayFulfillment(orderCode, "admin@vex360.com");

        assertTrue(res.isPresent());
        verify(boothProvisioningService).ensureBoothForApprovedRegistration(5);
    }
}
