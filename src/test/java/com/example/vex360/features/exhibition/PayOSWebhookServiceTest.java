package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import com.example.vex360.features.exhibition.repositories.ExhibitorRegistrationRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository.PaymentRoute;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.features.exhibition.services.impl.PayOSWebhookServiceImpl;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.services.ExhibitionTimelinePolicy;
import com.example.vex360.features.exhibition.services.ExhibitionParticipationPolicy;
import com.example.vex360.shared.enums.ExhibitionStatus;
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
    private PaymentFulfillmentService fulfillmentService;

    @Mock
    private StoragePackageService storagePackageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ExhibitionTimelinePolicy timelinePolicy;
    @Mock
    private ExhibitionParticipationPolicy participationPolicy;

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
        Exhibition exhibition = Exhibition.builder()
                .id(10)
                .status(ExhibitionStatus.PUBLISHED)
                .startDate(LocalDate.now().plusDays(10))
                .build();
        pendingRegistration = ExhibitorRegistration.builder()
                .id(1)
                .status(ExhibitorRegistrationStatus.PENDING_PAYMENT)
                .priceSnapshot(BigDecimal.valueOf(700000))
                .finalPriceSnapshot(BigDecimal.valueOf(1000000))
                .exhibitionPackage(ExhibitionPackage.builder().exhibition(exhibition).build())
                .company(Company.builder().id(UUID.randomUUID())
                        .name("Test Co").build())
                .build();
        org.mockito.Mockito.lenient().when(timelinePolicy.isRegistrationProcessingOpen(exhibition)).thenReturn(true);

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
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals("00", result.getCode());
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals(BigDecimal.valueOf(700000), pendingPayment.getSystemFee());
        assertEquals(BigDecimal.valueOf(300000), pendingPayment.getOrganizerPayout());
        assertEquals("payos_ref_123", pendingPayment.getPaymentReference());
        assertEquals(ExhibitorRegistrationStatus.APPROVED, pendingRegistration.getStatus());

        verify(paymentRepository).save(pendingPayment);
        verify(registrationRepository).save(pendingRegistration);
        verify(eventPublisher).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
        InOrder locks = inOrder(registrationRepository, paymentRepository);
        locks.verify(registrationRepository).findByIdForUpdate(pendingRegistration.getId());
        locks.verify(paymentRepository).findByOrderCodeForUpdate(pendingPayment.getOrderCode());
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
    void testHandleWebhook_MissingReference_ThrowsAppException() {
        Object mockBody = new Object();
        WebhookData successDataNoRef = mock(WebhookData.class);
        when(successDataNoRef.getOrderCode()).thenReturn(123456L);
        when(successDataNoRef.getCode()).thenReturn("00");
        when(successDataNoRef.getCurrency()).thenReturn("VND");
        when(successDataNoRef.getAmount()).thenReturn(1000000L);
        when(successDataNoRef.getDescription()).thenReturn("Registration");
        when(successDataNoRef.getAccountNumber()).thenReturn("123456789");
        when(successDataNoRef.getTransactionDateTime()).thenReturn("2026-08-07T10:00:00");
        when(successDataNoRef.getPaymentLinkId()).thenReturn("link_123");
        when(successDataNoRef.getDesc()).thenReturn("Success");
        when(successDataNoRef.getReference()).thenReturn(null);

        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successDataNoRef);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(mockBody));

        verify(paymentRepository, never()).findRouteByOrderCode(any());
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

        verify(storagePackageService).markPaidAndIncrementQuota(77);
        verify(fulfillmentService).updateReceiptSucceeded(123456L, null, null);
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void paidPayment_duplicateSuccessWebhook_isIdempotent() {
        Object mockBody = new Object();
        pendingPayment.setStatus(PaymentStatus.PAID);
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.APPROVED);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        WebhookData result = webhookServiceWrapper.handleWebhook(mockBody);

        assertNotNull(result);
        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void lateSuccessWebhook_recordsPaidButDoesNotApprove() {
        Object mockBody = new Object();
        Exhibition exhibition = pendingRegistration.getExhibitionPackage().getExhibition();
        when(timelinePolicy.isRegistrationProcessingOpen(exhibition)).thenReturn(false);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(mockBody)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(mockBody);

        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT, pendingRegistration.getStatus());
        verify(fulfillmentService).updateReceiptFailed(eq(pendingPayment.getOrderCode()), any(AppException.class));
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
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
        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
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

    @Test
    void handleWebhook_rejectsIncompleteRouteAndMissingLockedRows() {
        Object body = new Object();
        PaymentRoute route = mock(PaymentRoute.class);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        when(paymentRepository.findRouteByOrderCode(123456L)).thenReturn(Optional.of(route));
        when(route.getPaymentType()).thenReturn(PaymentType.EXHIBITION_REGISTRATION);

        when(route.getRegistrationId()).thenReturn(null);
        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));

        when(route.getRegistrationId()).thenReturn(99);
        when(registrationRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));

        when(route.getRegistrationId()).thenReturn(1);
        when(registrationRepository.findByIdForUpdate(1)).thenReturn(Optional.of(pendingRegistration));
        when(paymentRepository.findByOrderCodeForUpdate(123456L)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));
    }

    @Test
    void handleWebhook_auditsGenericFailureAfterVerification() {
        Object body = new Object();
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        when(paymentRepository.findRouteByOrderCode(123456L)).thenThrow(new RuntimeException("database"));

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));

        verify(fulfillmentService).updateReceiptFailed(eq(123456L), any(RuntimeException.class));
    }

    @Test
    void handleWebhook_propagatesAppExceptionBeforeOrderCodeIsKnown() {
        Object body = new Object();
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenThrow(new AppException(ErrorCode.UNCATCHED_EXCEPTION));

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));

        verify(fulfillmentService, never()).updateReceiptFailed(any(), any());
    }

    @Test
    void handleWebhook_rejectsLockedPaymentTypeAndRegistrationMismatch() {
        Object body = new Object();
        PaymentRoute route = mock(PaymentRoute.class);
        pendingPayment.setPaymentType(PaymentType.EXHIBITION_REGISTRATION);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        when(paymentRepository.findRouteByOrderCode(123456L)).thenReturn(Optional.of(route));
        when(paymentRepository.findByOrderCodeForUpdate(123456L)).thenReturn(Optional.of(pendingPayment));

        when(route.getPaymentType()).thenReturn(PaymentType.STORAGE_PACKAGE);
        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));

        when(route.getPaymentType()).thenReturn(PaymentType.EXHIBITION_REGISTRATION);
        when(route.getRegistrationId()).thenReturn(2);
        when(registrationRepository.findByIdForUpdate(2)).thenReturn(Optional.of(pendingRegistration));
        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));
    }

    @Test
    void handleWebhook_rejectsChangedProviderReference() {
        Object body = new Object();
        pendingPayment.setPaymentReference("original-reference");
        WebhookData changedReference = createWebhookData(
                123456L, 1000000L, "VND", "00", "link_123", "changed-reference");
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(changedReference);
        stubLockedPayment(pendingPayment);

        assertThrows(AppException.class, () -> webhookServiceWrapper.handleWebhook(body));
    }

    @Test
    void handleWebhook_matchingPaymentLinkAndReferenceSucceeds() {
        Object body = new Object();
        pendingPayment.setCheckoutUrl("https://payos.vn/link_123");
        pendingPayment.setPaymentReference("payos_ref_123");
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(body);

        assertEquals(PaymentStatus.PAID, pendingPayment.getStatus());
    }

    @Test
    void handleWebhook_missingPackageCannotApproveRegistration() {
        Object body = new Object();
        pendingRegistration.setExhibitionPackage(null);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(body);

        assertEquals(ExhibitorRegistrationStatus.PENDING_PAYMENT, pendingRegistration.getStatus());
        verify(fulfillmentService).updateReceiptFailed(eq(123456L), any(AppException.class));
    }

    @Test
    void handleWebhook_alreadyApprovedRegistrationDoesNotRepublishApproval() {
        Object body = new Object();
        pendingRegistration.setStatus(ExhibitorRegistrationStatus.APPROVED);
        when(payOS.webhooks()).thenReturn(webhookService);
        when(webhookService.verify(body)).thenReturn(successWebhookData);
        stubLockedPayment(pendingPayment);

        webhookServiceWrapper.handleWebhook(body);

        verify(eventPublisher, never()).publishEvent(any(ExhibitorRegistrationApprovedEvent.class));
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
