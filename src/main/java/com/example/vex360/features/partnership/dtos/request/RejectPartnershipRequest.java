package com.example.vex360.features.partnership.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin admin nhập khi từ chối partnership request")
public class RejectPartnershipRequest {
    @Schema(description = "Lý do từ chối hiển thị trong chi tiết request", example = "Thông tin công ty chưa đủ để xét duyệt.")
    @NotBlank(message = "Review note must not be blank")
    @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự.")
    private String reviewNote;
}
