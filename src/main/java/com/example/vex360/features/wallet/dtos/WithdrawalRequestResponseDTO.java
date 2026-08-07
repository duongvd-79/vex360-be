package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.wallet.enums.WithdrawalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalRequestResponseDTO {
    UUID uuid;
    UUID companyId;
    String companyName;
    BigDecimal amount;
    String currency;
    BigDecimal minimumAmountSnapshot;
    WithdrawalStatus status;
    String bankCodeSnapshot;
    String bankNameSnapshot;
    String accountNumberMaskedSnapshot;
    String accountNumberSnapshot;
    String accountHolderNameSnapshot;
    Instant requestedAt;
    Instant approvedAt;
    Instant paidAt;
    Instant rejectedAt;
    String rejectedReason;
    Instant canceledAt;
    String transferReference;
    String proofUrl;
    Instant createdAt;
    Instant updatedAt;
}
