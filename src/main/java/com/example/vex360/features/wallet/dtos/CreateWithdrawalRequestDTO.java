package com.example.vex360.features.wallet.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
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
public class CreateWithdrawalRequestDTO {
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "100000.00", message = "Số tiền rút tối thiểu là 100.000 VND")
    BigDecimal amount;
}
