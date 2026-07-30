package com.example.vex360.features.wallet.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class AdminReversalRequestDTO {
    @NotBlank(message = "Lý do hoàn/đảo tiền không được để trống")
    @Size(min = 3, max = 500, message = "Lý do hoàn/đảo tiền phải từ 3 đến 500 ký tự")
    String reason;
}
