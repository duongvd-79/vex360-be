package com.example.vex360.features.exhibition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.entities.PaymentReceipt;
import com.example.vex360.features.exhibition.jobs.PaymentReconciliationJob;
import com.example.vex360.features.exhibition.repositories.PaymentReceiptRepository;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.PaymentFulfillmentService;
import com.example.vex360.shared.enums.PaymentStatus;

import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock
    private PaymentReceiptRepository receiptRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentFulfillmentService fulfillmentService;

    @Mock
    private PayOSIntegrationService payOSIntegrationService;

    @InjectMocks
    private PaymentReconciliationJob reconciliationJob;

    @Test
    void runReconciliation_processesClaimableReceiptsAndUnfulfilledPayments() {
        PaymentReceipt claimable = PaymentReceipt.builder().orderCode(1001L).build();
        Payment unfulfilled = Payment.builder().orderCode(1002L).status(PaymentStatus.PAID).build();

        when(receiptRepository.findClaimableReceipts(any(), any(), any()))
                .thenReturn(List.of(claimable));
        when(paymentRepository.findUnfulfilledPaidPayments(any()))
                .thenReturn(List.of(unfulfilled));
        when(paymentRepository.findPendingExhibitionPayments(any()))
                .thenReturn(Collections.emptyList());

        reconciliationJob.runReconciliation();

        verify(fulfillmentService).processFulfillmentForOrderCode(1001L);
        verify(fulfillmentService).processFulfillmentForOrderCode(1002L);
    }

    @Test
    void runReconciliation_queriesProviderForPendingPayments() {
        Payment pendingPayment = Payment.builder().orderCode(1003L).status(PaymentStatus.PENDING).build();
        PaymentLink linkInfo = PaymentLink.builder()
                .id("link_1003")
                .orderCode(1003L)
                .amount(100000L)
                .amountPaid(100000L)
                .amountRemaining(0L)
                .status(PaymentLinkStatus.PAID)
                .createdAt("2026-07-28 12:00:00")
                .transactions(Collections.emptyList())
                .canceledAt("")
                .cancellationReason("")
                .build();

        when(receiptRepository.findClaimableReceipts(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findUnfulfilledPaidPayments(any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findPendingExhibitionPayments(any()))
                .thenReturn(List.of(pendingPayment));
        when(payOSIntegrationService.getPaymentLinkInformation(1003L))
                .thenReturn(linkInfo);

        reconciliationJob.runReconciliation();

        verify(paymentRepository).save(pendingPayment);
        verify(fulfillmentService).processFulfillmentForOrderCode(1003L);
    }
}
