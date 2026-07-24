package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.StoragePaymentService;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@ExtendWith(MockitoExtension.class)
class StoragePaymentServiceUnitTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PayOSIntegrationService payOSIntegrationService;
    @Mock
    CreatePaymentLinkResponse paymentLinkResponse;
    @InjectMocks
    StoragePaymentService service;

    @Test
    void createsStoragePaymentInsideExhibitionFeature() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(99L, 500L, "Storage", "return", "cancel"))
                .thenReturn(paymentLinkResponse);
        when(paymentLinkResponse.getCheckoutUrl()).thenReturn("checkout-url");

        String checkoutUrl = service.createPayment(7, 99L, 500L, "Storage", "return", "cancel");

        assertEquals("checkout-url", checkoutUrl);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, Mockito.times(2)).save(paymentCaptor.capture());
        assertEquals(7, paymentCaptor.getValue().getStoragePackageOrderId());
    }
}
