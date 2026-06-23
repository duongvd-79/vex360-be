package com.example.vex360.features.partnership.dtos.request;

import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Form gửi yêu cầu hợp tác dành cho guest hoặc user đã đăng nhập")
public class SubmitPartnershipRequest {
    @Schema(description = "Tên người đại diện gửi yêu cầu", example = "Nguyen Van An")
    @NotBlank(message = "Requester name must not be blank")
    private String requesterName;

    @Schema(description = "Email liên hệ của người/công ty gửi yêu cầu", example = "partner@example.com")
    @NotBlank(message = "Requester email must not be blank")
    @Email(message = "Requester email is invalid")
    private String requesterEmail;

    @Schema(description = "Số điện thoại liên hệ, định dạng 10 số bắt đầu bằng 0", example = "0912345678")
    @NotBlank(message = "Requester phone number must not be blank")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại không hợp lệ!")
    private String requesterPhoneNumber;

    @Schema(description = "Tên công ty hoặc tổ chức muốn hợp tác", example = "Vex360 Partner")
    @NotBlank(message = "Organization name must not be blank")
    private String organizationName;

    @Schema(
            description = "Role muốn được cấp sau khi admin duyệt. Chỉ chấp nhận EXHIBITOR hoặc ORGANIZER.",
            example = "EXHIBITOR",
            allowableValues = {"EXHIBITOR", "ORGANIZER"})
    @NotNull(message = "Requested role must not be null")
    private Role requestedRole;

    @Schema(description = "Nội dung hoặc ghi chú gửi cho admin", example = "We want to join Vex360 as an exhibitor.")
    private String message;

    @Schema(description = "Người gửi đã đồng ý chính sách hợp tác", example = "true")
    @AssertTrue(message = "Policy must be accepted")
    private Boolean acceptedPolicy;
}
