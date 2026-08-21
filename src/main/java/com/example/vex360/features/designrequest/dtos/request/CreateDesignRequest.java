package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDesignRequest {
    @NotNull(message = "Booth id không được để trống")
    private UUID boothId;

    private List<@NotNull(message = "ID sản phẩm không được để trống.") UUID> productIds;

    private List<@NotNull(message = "ID media không được để trống.") UUID> mediaAssetIds;

    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 320, message = "Email liên hệ không được vượt quá 320 ký tự")
    private String contactEmail;

    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại liên hệ không hợp lệ")
    private String contactPhone;

    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự.")
    private String note;

    public CreateDesignRequest(UUID boothId, String note) {
        this(boothId, List.of(), List.of(), null, null, note);
    }
}
