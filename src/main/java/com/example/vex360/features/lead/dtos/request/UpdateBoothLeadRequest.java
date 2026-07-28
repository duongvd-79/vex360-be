package com.example.vex360.features.lead.dtos.request;

import com.example.vex360.features.lead.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBoothLeadRequest(
        @NotNull(message = "Vui lòng chọn trạng thái lead.")
        LeadStatus status,

        @Size(max = 2000, message = "Ghi chú không được vượt quá 2.000 ký tự.")
        String exhibitorNote) {
}
