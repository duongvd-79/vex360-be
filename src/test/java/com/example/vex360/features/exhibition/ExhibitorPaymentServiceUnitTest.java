package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.PaymentHistoryResponseDTO;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.exhibition.services.ExhibitorPaymentService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;

@ExtendWith(MockitoExtension.class)
class ExhibitorPaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private ExhibitorPaymentService exhibitorPaymentService;

    @Test
    void getHistoryReturnsAllPaymentTypesForCurrentCompany() {
        User currentUser = User.builder().id(UUID.randomUUID()).build();
        Company company = Company.builder().id(UUID.randomUUID()).build();
        PageRequest pageable = PageRequest.of(0, 10);
        Instant createdAt = Instant.parse("2026-08-03T08:00:00Z");

        Payment exhibitionPayment = Payment.builder()
                .id(1)
                .orderCode(1001L)
                .paymentType(PaymentType.EXHIBITION_REGISTRATION)
                .amount(BigDecimal.valueOf(200_000))
                .currency("VND")
                .paymentProvider("PAYOS")
                .status(PaymentStatus.PAID)
                .createdAt(createdAt)
                .build();
        Payment storagePayment = Payment.builder()
                .id(2)
                .orderCode(1002L)
                .paymentType(PaymentType.STORAGE_PACKAGE)
                .amount(BigDecimal.valueOf(100_000))
                .currency("VND")
                .paymentProvider("PAYOS")
                .status(PaymentStatus.PENDING)
                .createdAt(createdAt.minusSeconds(60))
                .build();

        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        when(paymentRepository.findHistoryByCompanyId(company.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(exhibitionPayment, storagePayment), pageable, 2));

        PageResponse<PaymentHistoryResponseDTO> result = exhibitorPaymentService.getHistory(currentUser, pageable);

        assertEquals(List.of(PaymentType.EXHIBITION_REGISTRATION, PaymentType.STORAGE_PACKAGE),
                result.getContent().stream().map(PaymentHistoryResponseDTO::getPaymentType).toList());
        assertEquals(2, result.getTotalElements());
        assertEquals(1001L, result.getContent().getFirst().getOrderCode());
        assertEquals(PaymentStatus.PAID, result.getContent().getFirst().getStatus());
        verify(paymentRepository).findHistoryByCompanyId(company.getId(), pageable);
    }
}
