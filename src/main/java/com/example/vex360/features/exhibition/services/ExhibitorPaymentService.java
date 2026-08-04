package com.example.vex360.features.exhibition.services;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.dtos.response.PaymentHistoryResponseDTO;
import com.example.vex360.features.exhibition.entities.Payment;
import com.example.vex360.features.exhibition.repositories.PaymentRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExhibitorPaymentService {

    private final PaymentRepository paymentRepository;
    private final CompanyService companyService;

    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponseDTO> getHistory(User currentUser, Pageable pageable) {
        Company company = companyService.getCompanyEntityForCurrentUser(currentUser);
        return PageResponse.from(paymentRepository.findHistoryByCompanyId(company.getId(), pageable).map(this::toDTO));
    }

    private PaymentHistoryResponseDTO toDTO(Payment payment) {
        return PaymentHistoryResponseDTO.builder()
                .id(payment.getId())
                .orderCode(payment.getOrderCode())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentProvider(payment.getPaymentProvider())
                .paymentReference(payment.getPaymentReference())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
