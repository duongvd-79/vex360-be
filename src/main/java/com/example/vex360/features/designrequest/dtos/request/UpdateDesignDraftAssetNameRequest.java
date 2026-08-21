package com.example.vex360.features.designrequest.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDesignDraftAssetNameRequest {
    @NotBlank(message = "Ten file khong duoc de trong")
    @Size(max = 255, message = "Ten file khong duoc vuot qua 255 ky tu")
    private String fileName;
}
