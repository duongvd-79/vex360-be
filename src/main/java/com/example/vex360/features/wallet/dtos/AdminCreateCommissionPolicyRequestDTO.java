package com.example.vex360.features.wallet.dtos;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class AdminCreateCommissionPolicyRequestDTO {
    @NotNull(message = "Tỷ lệ hoa hồng (basis points) không được để trống")
    @Min(value = 0, message = "Tỷ lệ hoa hồng không được nhỏ hơn 0")
    @Max(value = 9999, message = "Tỷ lệ hoa hồng không được vượt quá 9999 (99.99%)")
    Integer rateBasisPoints;

    Instant effectiveAt;
}
