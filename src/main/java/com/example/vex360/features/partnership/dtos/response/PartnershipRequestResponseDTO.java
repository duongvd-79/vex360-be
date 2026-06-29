package com.example.vex360.features.partnership.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của một yêu cầu hợp tác")
public class PartnershipRequestResponseDTO {
    @Schema(description = "ID partnership request", example = "84257b4d-e9f3-4a41-a9d9-b0e8d1b4d9e3")
    private UUID id;

    @Schema(description = "ID user đã login gửi request; null nếu là guest request", example = "0a11c6a6-65a5-4e37-8c5d-25091f25c4dc")
    private UUID submittedByUserId;

    @Schema(description = "Email user đã login gửi request; null nếu là guest request", example = "user@example.com")
    private String submittedByUserEmail;

    @Schema(description = "Tên người đại diện gửi yêu cầu", example = "Nguyen Van An")
    private String requesterName;

    @Schema(description = "Email liên hệ trên form yêu cầu", example = "partner@example.com")
    private String requesterEmail;

    @Schema(description = "Số điện thoại liên hệ trên form yêu cầu", example = "0912345678")
    private String requesterPhoneNumber;

    @Schema(description = "Tên công ty hoặc tổ chức", example = "Vex360 Partner")
    private String organizationName;

    @Schema(
            description = "Role được yêu cầu",
            example = "EXHIBITOR",
            allowableValues = {"EXHIBITOR", "ORGANIZER"})
    private String requestedRole;

    @Schema(description = "Nội dung hoặc ghi chú người gửi nhập", example = "We want to join Vex360 as an exhibitor.")
    private String message;

    @Schema(description = "Người gửi đã đồng ý chính sách hợp tác", example = "true")
    private Boolean acceptedPolicy;

    @Schema(
            description = "Trạng thái xét duyệt request",
            example = "PENDING",
            allowableValues = {"PENDING", "APPROVED", "REJECTED"})
    private String status;

    @Schema(description = "Ghi chú xét duyệt của admin, thường dùng khi reject", example = "Thông tin công ty chưa đủ để xét duyệt.")
    private String reviewNote;

    @Schema(description = "Thời điểm request được tạo", example = "2026-06-22T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm request được duyệt hoặc từ chối", example = "2026-06-22T11:00:00")
    private LocalDateTime reviewedAt;
}
