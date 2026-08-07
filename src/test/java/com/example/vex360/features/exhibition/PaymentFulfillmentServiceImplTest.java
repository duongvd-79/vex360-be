package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentReceiptRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.impl.PaymentFulfillmentServiceImpl;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.PaymentReceiptStatus;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.model.webhooks.WebhookData;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentFulfillmentServiceImplTest {

    @Mock
    private PaymentReceiptRepository receiptRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ExhibitorRegistrationRepository registrationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;

    @Mock
    private PayOSIntegrationService payOSIntegrationService;

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
        PaymentReceipt existing = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.SUCCEEDED)
                .build();

        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(existing));

        PaymentReceipt result = fulfillmentService.recordReceipt(data);

        assertEquals(existing, result);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void recordReceipt_rejectsNullPayload() {
        assertThrows(AppException.class, () -> fulfillmentService.recordReceipt(null));
    }

    @Test
    void updateReceiptSucceeded_setsSucceededStatusAndBoothId() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING)
                .build();
        UUID boothId = UUID.randomUUID();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptSucceeded(orderCode, 5, boothId);

        assertEquals(PaymentReceiptStatus.SUCCEEDED, receipt.getStatus());
        assertEquals(5, receipt.getRegistrationId());
        assertEquals(boothId, receipt.getBoothId());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void updateReceiptSucceeded_ignoresNullAndCreatesMissingReceipt() {
        fulfillmentService.updateReceiptSucceeded(null, 1, null);
        verify(receiptRepository, never()).save(any());

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.empty());
        when(receiptRepository.save(any(PaymentReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fulfillmentService.updateReceiptSucceeded(orderCode, 1, null);

        verify(receiptRepository).save(any(PaymentReceipt.class));
    }

    @Test
    void updateReceiptFailed_setsRetryableFailedWithBackoff() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING)
                .retryCount(0).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptFailed(orderCode, new RuntimeException("DB Connection Timeout"));

        assertEquals(PaymentReceiptStatus.RETRYABLE_FAILED, receipt.getStatus());
        assertEquals(1, receipt.getRetryCount());
        assertNotNull(receipt.getNextRetryAt());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void updateReceiptFailed_dependencyInvalid_movesToManualReview() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING)
                .retryCount(0).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

        fulfillmentService.updateReceiptFailed(orderCode, new AppException(ErrorCode.REGISTRATION_DEPENDENCY_INVALID));

        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());
        assertEquals(1, receipt.getRetryCount());
        verify(receiptRepository).save(receipt);
    }

    @Test
    void updateReceiptFailed_coversNullMissingReceiptAndRetryLimit() {
        fulfillmentService.updateReceiptFailed(null, null);
        verify(receiptRepository, never()).save(any());

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.empty());
        when(receiptRepository.save(any(PaymentReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        fulfillmentService.updateReceiptFailed(orderCode, null);

        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode)
                .status(PaymentReceiptStatus.RETRYABLE_FAILED).retryCount(4).build();
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        fulfillmentService.updateReceiptFailed(orderCode, new RuntimeException("retry"));

        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());
        assertEquals(5, receipt.getRetryCount());
    }

    @Test
    void updateReceiptFailed_coversAllNonRetryableCodes() {
        for (ErrorCode errorCode : new ErrorCode[] {
                ErrorCode.REGISTRATION_NOT_FOUND,
                ErrorCode.REGISTRATION_CLOSED }) {
            PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode)
                    .status(PaymentReceiptStatus.PENDING).retryCount(0).build();
            when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));

            fulfillmentService.updateReceiptFailed(orderCode, new AppException(errorCode));

            assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());
        }

        PaymentReceipt retryable = PaymentReceipt.builder().orderCode(orderCode)
                .status(PaymentReceiptStatus.PENDING).retryCount(0).build();
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(retryable));
        fulfillmentService.updateReceiptFailed(orderCode, new AppException(ErrorCode.UNCATCHED_EXCEPTION));
        assertEquals(PaymentReceiptStatus.RETRYABLE_FAILED, retryable.getStatus());
    }

    @Test
    void processFulfillmentForOrderCode_provisionsBoothAndUpdatesReceipt() {
        Exhibition exhibition = Exhibition.builder().status(ExhibitionStatus.PUBLISHED).build();
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Payment payment = Payment.builder().orderCode(orderCode).status(PaymentStatus.PAID)
                .paymentType(PaymentType.EXHIBITION_REGISTRATION).exhibitorRegistration(reg).build();
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.SUCCEEDED)
                .build();

        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(reg.getId())));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(reg));
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(receipt));
        when(timelinePolicy.isRegistrationOpen(exhibition)).thenReturn(true);

        Optional<PaymentReceipt> res = fulfillmentService.processFulfillmentForOrderCode(orderCode);

        assertTrue(res.isPresent());
        assertEquals(PaymentReceiptStatus.SUCCEEDED, res.get().getStatus());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, reg.getStatus());
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void processFulfillmentForOrderCode_latePaymentStaysUnapproved() {
        Exhibition exhibition = Exhibition.builder().status(ExhibitionStatus.PUBLISHED).build();
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Payment payment = Payment.builder().orderCode(orderCode).status(PaymentStatus.PAID)
                .paymentType(PaymentType.EXHIBITION_REGISTRATION).exhibitorRegistration(reg).build();
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode).status(PaymentReceiptStatus.PENDING)
                .retryCount(0).build();

        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(reg));
        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(reg.getId())));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(receipt));
        when(timelinePolicy.isRegistrationOpen(exhibition)).thenReturn(false);

        fulfillmentService.processFulfillmentForOrderCode(orderCode);

        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT, reg.getStatus());
        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void processFulfillmentForOrderCode_rejectsNullAndUnsupportedRoutes() {
        assertTrue(fulfillmentService.processFulfillmentForOrderCode(null).isEmpty());

        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(
                Optional.empty(),
                Optional.of(routeFor(PaymentType.STORAGE_PACKAGE, null)),
                Optional.of(routeFor(PaymentType.EXHIBITION_REGISTRATION, null)));

        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isEmpty());
        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isEmpty());
        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isEmpty());
    }

    @Test
    void processFulfillmentForOrderCode_handlesMissingRegistrationAndPayment() {
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode)
                .status(PaymentReceiptStatus.PENDING).retryCount(0).build();
        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(5)));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.empty());
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(receipt));

        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isPresent());
        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, receipt.getStatus());

        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(5).build();
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(registration));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode))
                .thenReturn(Optional.empty(), Optional.of(Payment.builder().status(PaymentStatus.PENDING).build()));

        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isEmpty());
        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isEmpty());
    }

    @Test
    void processFulfillmentForOrderCode_rejectsMissingPackageAndInvalidStatus() {
        Exhibition exhibition = Exhibition.builder().build();
        ExhibitorRegistration missingPackage = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT).build();
        ExhibitorRegistration rejected = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.REJECTED)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Payment payment = Payment.builder().status(PaymentStatus.PAID).build();
        PaymentReceipt first = PaymentReceipt.builder().retryCount(0).build();
        PaymentReceipt second = PaymentReceipt.builder().retryCount(0).build();

        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(5)));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(missingPackage), Optional.of(rejected));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(receiptRepository.findByOrderCodeForUpdate(orderCode))
                .thenReturn(Optional.of(first), Optional.of(second));
        when(receiptRepository.findByOrderCode(orderCode))
                .thenReturn(Optional.of(first), Optional.of(second));
        when(timelinePolicy.isRegistrationOpen(exhibition)).thenReturn(true);

        fulfillmentService.processFulfillmentForOrderCode(orderCode);
        fulfillmentService.processFulfillmentForOrderCode(orderCode);

        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, first.getStatus());
        assertEquals(PaymentReceiptStatus.MANUAL_REVIEW, second.getStatus());
    }

    @Test
    void processFulfillmentForOrderCode_recordsUnexpectedFulfillmentFailure() {
        Exhibition exhibition = Exhibition.builder().build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Payment payment = Payment.builder().status(PaymentStatus.PAID).build();
        PaymentReceipt receipt = PaymentReceipt.builder().orderCode(orderCode)
                .status(PaymentReceiptStatus.PENDING).retryCount(0).build();

        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(5)));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(registration));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(PaymentReceipt.class)))
                .thenThrow(new RuntimeException("write failed"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(receipt));
        when(timelinePolicy.isRegistrationOpen(exhibition)).thenReturn(true);

        assertTrue(fulfillmentService.processFulfillmentForOrderCode(orderCode).isPresent());
        assertEquals(PaymentReceiptStatus.RETRYABLE_FAILED, receipt.getStatus());
    }

    private PaymentRepository.PaymentRoute routeFor(Integer registrationId) {
        return routeFor(PaymentType.EXHIBITION_REGISTRATION, registrationId);
    }

    private PaymentRepository.PaymentRoute routeFor(PaymentType paymentType, Integer registrationId) {
        return new PaymentRepository.PaymentRoute() {
            public PaymentType getPaymentType() {
                return paymentType;
            }

            public Integer getRegistrationId() {
                return registrationId;
            }
        };
    }

    @Test
    void replayFulfillment_resetsManualReviewAndFulfills_ReplayAlreadyApprovedDoesNotPublishEvent() {
        Exhibition exhibition = Exhibition.builder().status(ExhibitionStatus.PUBLISHED).build();
        ExhibitorRegistration reg = ExhibitorRegistration.builder().id(5)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .build();
        Payment payment = Payment.builder().orderCode(orderCode).status(PaymentStatus.PAID)
                .paymentType(PaymentType.EXHIBITION_REGISTRATION).exhibitorRegistration(reg).build();
        PaymentReceipt manualReceipt = PaymentReceipt.builder().orderCode(orderCode)
                .status(PaymentReceiptStatus.MANUAL_REVIEW).build();

        when(receiptRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(manualReceipt));
        when(paymentRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(payment));
        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.of(routeFor(reg.getId())));
        when(registrationRepository.findByIdForUpdate(5)).thenReturn(Optional.of(reg));
        when(receiptRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(manualReceipt));
        when(timelinePolicy.isRegistrationOpen(exhibition)).thenReturn(true);

        Optional<PaymentReceipt> res = fulfillmentService.replayFulfillment(orderCode, "admin@vex360.com");

        assertTrue(res.isPresent());
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
    }

    @Test
    void replayFulfillment_leavesNonManualReceiptAndHandlesMissingReceipt() {
        PaymentReceipt pending = PaymentReceipt.builder().status(PaymentReceiptStatus.PENDING).build();
        when(receiptRepository.findByOrderCodeForUpdate(orderCode))
                .thenReturn(Optional.of(pending), Optional.empty());
        when(paymentRepository.findRouteByOrderCode(orderCode)).thenReturn(Optional.empty());

        assertTrue(fulfillmentService.replayFulfillment(orderCode, "admin").isEmpty());
        assertTrue(fulfillmentService.replayFulfillment(orderCode, "admin").isEmpty());
        verify(receiptRepository, never()).save(pending);
    }
}
