package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.wallet.enums.WalletBucket;
import com.example.vex360.features.wallet.enums.WalletTransactionType;

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
public class WalletTransactionResponseDTO {
    UUID transactionUuid;
    UUID exhibitionUuid;
    String exhibitionName;
    String exhibitorCompanyName;
    UUID registrationUuid;
    Long orderCode;
    BigDecimal amount;
    BigDecimal systemFee;
    BigDecimal organizerPayout;
    WalletTransactionType type;
    WalletBucket fromBucket;
    WalletBucket toBucket;
    BigDecimal pendingAfter;
    BigDecimal availableAfter;
    BigDecimal reservedAfter;
    BigDecimal withdrawnAfter;
    String reason;
    Instant createdAt;
}
