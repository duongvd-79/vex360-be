package com.example.vex360.features.wallet.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class AdminMarkPaidWithdrawalRequestDTO {
    @NotBlank(message = "Mã giao dịch chuyển khoản không được để trống")
    @Size(min = 3, max = 100, message = "Mã giao dịch chuyển khoản phải từ 3 đến 100 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "Mã giao dịch chỉ được chứa chữ cái, số, gạch ngang")
    String transferReference;

    @Size(max = 1000, message = "Link bằng chứng không vượt quá 1000 ký tự")
    @Pattern(regexp = "^(https?://.+)?$", message = "Link bằng chứng phải là URL HTTP/HTTPS hợp lệ")
    String proofUrl;
}
