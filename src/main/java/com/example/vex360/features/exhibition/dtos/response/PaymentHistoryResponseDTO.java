package com.example.vex360.features.exhibition.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.vex360.shared.enums.PaymentStatus;
import com.example.vex360.shared.enums.PaymentType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentHistoryResponseDTO {
    private Integer id;
    private Long orderCode;
    private PaymentType paymentType;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String paymentProvider;
    private String paymentReference;
    private Instant paidAt;
    private Instant createdAt;
}
