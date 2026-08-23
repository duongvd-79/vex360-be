package com.example.vex360.features.hall.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertHallItemRequest {
    @NotBlank(message = "Tên nội dung trưng bày không được để trống")
    @Size(max = 255, message = "Tên nội dung trưng bày không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả nội dung trưng bày không được vượt quá 5000 ký tự")
    private String description;

    @NotNull(message = "Tệp phương tiện của nội dung trưng bày không được để trống")
    private UUID mediaAssetId;

    @PositiveOrZero(message = "Thứ tự hiển thị phải lớn hơn hoặc bằng 0")
    private Integer displayOrder;
}
