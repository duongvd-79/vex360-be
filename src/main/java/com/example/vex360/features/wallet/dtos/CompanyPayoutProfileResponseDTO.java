package com.example.vex360.features.wallet.dtos;

import java.time.Instant;
import java.util.UUID;

import com.example.vex360.features.wallet.enums.PayoutProfileStatus;

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
public class CompanyPayoutProfileResponseDTO {
    UUID companyId;
    String companyName;
    String bankCode;
    String bankNameSnapshot;
    String accountNumberMasked;
    String accountHolderName;
    PayoutProfileStatus status;
    Instant verifiedAt;
    Instant rejectedAt;
    String rejectedReason;
    Instant createdAt;
    Instant updatedAt;
}
