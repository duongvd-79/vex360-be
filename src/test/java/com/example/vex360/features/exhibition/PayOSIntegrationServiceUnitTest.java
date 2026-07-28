package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.services.impl.PayOSIntegrationServiceImpl;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;

@ExtendWith(MockitoExtension.class)
class PayOSIntegrationServiceUnitTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;

    @InjectMocks
    private PayOSIntegrationServiceImpl payOSIntegrationService;

    @BeforeEach
    void setUp() {
        // No explicit setup needed
    }

    @Test
    void testCreatePaymentLink_Success() {
        CreatePaymentLinkResponse mockResponse = mock(CreatePaymentLinkResponse.class);
        when(mockResponse.getCheckoutUrl()).thenReturn("http://checkout");

        when(payOS.paymentRequests().create(any())).thenReturn(mockResponse);

        CreatePaymentLinkResponse response = payOSIntegrationService.createPaymentLink(
                123456L, 1000L, "test description", "http://return", "http://cancel");

        assertNotNull(response);
        assertEquals("http://checkout", response.getCheckoutUrl());
    }

    @Test
    void testCreatePaymentLink_ThrowsException_CatchesAndThrowsAppException() {
        when(payOS.paymentRequests().create(any())).thenThrow(new RuntimeException("API error"));

        AppException exception = assertThrows(AppException.class, () -> {
            payOSIntegrationService.createPaymentLink(
                    123456L, 1000L, "test description", "http://return", "http://cancel");
        });

        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
    }

    @Test
    void testGetPaymentLinkInformation_Success() {
        PaymentLink mockLink = mock(PaymentLink.class);
        when(payOS.paymentRequests().get(123456L)).thenReturn(mockLink);

        var result = payOSIntegrationService.getPaymentLinkInformation(123456L);

        assertNotNull(result);
        assertEquals(mockLink, result);
    }

    @Test
    void testGetPaymentLinkInformation_ThrowsException() {
        when(payOS.paymentRequests().get(123456L)).thenThrow(new RuntimeException("API error"));

        AppException exception = assertThrows(AppException.class, () -> {
            payOSIntegrationService.getPaymentLinkInformation(123456L);
        });

        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
    }

    @Test
    void testCancelPaymentLink_Success() {
        PaymentLink mockLink = mock(PaymentLink.class);
        when(payOS.paymentRequests().cancel(123456L, "cancellation reason")).thenReturn(mockLink);

        var result = payOSIntegrationService.cancelPaymentLink(123456L, "cancellation reason");

        assertNotNull(result);
        assertEquals(mockLink, result);
    }

    @Test
    void testCancelPaymentLink_ThrowsException() {
        when(payOS.paymentRequests().cancel(123456L, "cancellation reason"))
                .thenThrow(new RuntimeException("API error"));

        AppException exception = assertThrows(AppException.class, () -> {
            payOSIntegrationService.cancelPaymentLink(123456L, "cancellation reason");
        });

        assertEquals(ErrorCode.UNCATCHED_EXCEPTION, exception.getErrorCode());
    }
}
