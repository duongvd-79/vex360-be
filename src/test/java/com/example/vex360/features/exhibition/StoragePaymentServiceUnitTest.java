package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.PayOSIntegrationService;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@ExtendWith(MockitoExtension.class)
class StoragePaymentServiceUnitTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PayOSIntegrationService payOSIntegrationService;
    @Mock
    StoragePackageService storagePackageService;
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

    @Test
    void createsPendingStorageOrderAndReturnsCheckoutResponse() {
        User user = Mockito.mock(User.class);
        CreateStoragePackageOrderRequest request = Mockito.mock(CreateStoragePackageOrderRequest.class);
        StoragePackageOrder order = Mockito.mock(StoragePackageOrder.class);
        StoragePackageOrderResponseDTO response = Mockito.mock(StoragePackageOrderResponseDTO.class);
        when(storagePackageService.createPendingOrder(user, request)).thenReturn(order);
        when(order.getId()).thenReturn(7);
        when(order.getOrderCode()).thenReturn(99L);
        when(order.getAmountVnd()).thenReturn(500L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(99L, 500L, "Nang cap luu tru", null, null))
                .thenReturn(paymentLinkResponse);
        when(paymentLinkResponse.getCheckoutUrl()).thenReturn("checkout-url");
        when(storagePackageService.updateOrderCheckoutUrl(7, "checkout-url")).thenReturn(response);

        assertSame(response, service.createStorageOrderPayment(user, request));
    }

    @Test
    void marksPaymentFailedWhenPaymentLinkCreationFails() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOSIntegrationService.createPaymentLink(99L, 500L, "Storage", "return", "cancel"))
                .thenThrow(new RuntimeException("PayOS unavailable"));

        AppException exception = assertThrows(AppException.class,
                () -> service.createPayment(7, 99L, 500L, "Storage", "return", "cancel"));

        assertEquals(ErrorCode.PAYMENT_LINK_UNAVAILABLE, exception.getErrorCode());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, Mockito.times(2)).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.FAILED, paymentCaptor.getValue().getStatus());
    }
}
