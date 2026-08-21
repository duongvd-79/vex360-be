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
public class UpdatePayoutProfileRequestDTO {
    @NotBlank(message = "Mã ngân hàng không được để trống")
    @Size(min = 2, max = 20, message = "Mã ngân hàng phải từ 2 đến 20 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "Mã ngân hàng chỉ được chứa chữ cái, số, gạch ngang")
    String bankCode;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(min = 2, max = 100, message = "Tên ngân hàng phải từ 2 đến 100 ký tự")
    String bankNameSnapshot;

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    @Size(min = 4, max = 30, message = "Số tài khoản phải từ 4 đến 30 ký tự")
    @Pattern(regexp = "^[0-9A-Za-z]+$", message = "Số tài khoản ngân hàng không hợp lệ")
    String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(min = 2, max = 100, message = "Tên chủ tài khoản phải từ 2 đến 100 ký tự")
    String accountHolderName;
}
