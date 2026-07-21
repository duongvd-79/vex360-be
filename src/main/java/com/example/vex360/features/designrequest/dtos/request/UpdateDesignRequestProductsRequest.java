package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDesignRequestProductsRequest {
    @NotNull(message = "Danh sách sản phẩm không được để trống")
    private List<UUID> productIds;
}
