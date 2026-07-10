package com.example.vex360.features.exhibition.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitorRegistrationRequestDTO {
    @NotNull(message = "ID gói triển lãm không được để trống")
    private Integer exhibitionPackageId;

    @NotBlank(message = "Ly do tham gia khong duoc de trong")
    @Size(max = 1000, message = "Ly do tham gia khong duoc vuot qua 1000 ky tu")
    private String participationReason;
}
