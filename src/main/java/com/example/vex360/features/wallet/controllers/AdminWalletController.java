package com.example.vex360.features.wallet.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.wallet.dtos.AdminCreateCommissionPolicyRequestDTO;
import com.example.vex360.features.wallet.dtos.AdminMarkPaidWithdrawalRequestDTO;
import com.example.vex360.features.wallet.dtos.AdminRejectWithdrawalRequestDTO;
import com.example.vex360.features.wallet.dtos.AdminReversalRequestDTO;
import com.example.vex360.features.wallet.dtos.CommissionPolicyResponseDTO;
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.ReconciliationReportDTO;
import com.example.vex360.features.wallet.dtos.WithdrawalRequestResponseDTO;
import com.example.vex360.features.wallet.entities.CommissionPolicy;
import com.example.vex360.features.wallet.enums.PayoutProfileStatus;
import com.example.vex360.features.wallet.enums.WithdrawalStatus;
import com.example.vex360.features.wallet.services.CommissionPolicyService;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.OrganizerWalletDomainService;
import com.example.vex360.features.wallet.services.OrganizerWalletReconciliationService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin Wallet & Payout Management", description = "API dành cho Admin quản lý tài khoản nhận tiền, rút tiền, hoa hồng và đảo giao dịch")
public class AdminWalletController extends BaseController {

    CompanyPayoutProfileService payoutProfileService;
    WithdrawalRequestService withdrawalRequestService;
    CommissionPolicyService commissionPolicyService;
    OrganizerWalletDomainService walletDomainService;
    OrganizerWalletReconciliationService reconciliationService;

    @GetMapping("/payout-profiles")
    @Operation(summary = "Danh sách tài khoản nhận tiền", description = "Admin xem danh sách tài khoản nhận tiền của các doanh nghiệp, lọc theo trạng thái")
    public ResponseEntity<ApiResponse<PageResponse<CompanyPayoutProfileResponseDTO>>> getPayoutProfiles(
            @RequestParam(name = "status", required = false) PayoutProfileStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(payoutProfileService.getProfilesForAdmin(status, pageable));
    }

    @GetMapping("/payout-profiles/{companyId}/full-account")
    @Operation(summary = "Xem số tài khoản ngân hàng đầy đủ", description = "Chỉ sử dụng khi Admin thực hiện chuyển khoản ngoài hệ thống")
    public ResponseEntity<ApiResponse<Map<String, String>>> getFullAccount(
            @PathVariable("companyId") UUID companyId) {
        String plainAccount = payoutProfileService.getFullAccountNumberForAdmin(companyId);
        return ok(Map.of("accountNumber", plainAccount));
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Danh sách yêu cầu rút tiền", description = "Lọc danh sách yêu cầu rút tiền theo trạng thái hoặc companyId")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalRequestResponseDTO>>> getWithdrawals(
            @RequestParam(name = "status", required = false) WithdrawalStatus status,
            @RequestParam(name = "companyId", required = false) UUID companyId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(withdrawalRequestService.getWithdrawalRequestsForAdmin(status, companyId, pageable));
    }

    @GetMapping("/withdrawals/{uuid}")
    @Operation(summary = "Chi tiết yêu cầu rút tiền", description = "Xem chi tiết thông tin đơn rút tiền")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> getWithdrawalDetails(
            @PathVariable("uuid") UUID uuid) {
        return ok(withdrawalRequestService.getWithdrawalRequestDetailsForAdmin(uuid));
    }

    @GetMapping("/withdrawals/{uuid}/full-account")
    @Operation(summary = "Xem số tài khoản ngân hàng đầy đủ từ snapshot đơn rút tiền", description = "Admin lấy số tài khoản ngân hàng snapshot theo đơn rút tiền")
    public ResponseEntity<ApiResponse<Map<String, String>>> getFullWithdrawalAccount(
            @PathVariable("uuid") UUID uuid) {
        String plainAccount = withdrawalRequestService.getFullWithdrawalAccountNumberForAdmin(uuid);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(Map.of("accountNumber", plainAccount)));
    }

    @PostMapping("/withdrawals/{uuid}/approve")
    @Operation(summary = "Phê duyệt đơn rút tiền", description = "Chuyển đơn từ PENDING sang APPROVED")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> approveWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID uuid) {
        return ok(withdrawalRequestService.approveWithdrawalRequestForAdmin(userDetails.getUser(), uuid));
    }

    @PostMapping("/withdrawals/{uuid}/reject")
    @Operation(summary = "Từ chối đơn rút tiền", description = "Từ chối đơn rút tiền (PENDING hoặc APPROVED) và giải phóng số tiền đã giữ lại vào availableBalance")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> rejectWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID uuid,
            @Valid @RequestBody AdminRejectWithdrawalRequestDTO request) {
        return ok(withdrawalRequestService.rejectWithdrawalRequestForAdmin(userDetails.getUser(), uuid,
                request.getRejectedReason()));
    }

    @PostMapping("/withdrawals/{uuid}/mark-paid")
    @Operation(summary = "Xác nhận đã chuyển khoản", description = "Bắt buộc nhập mã giao dịch chuyển khoản unique (transferReference) để hoàn tất đơn rút")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> markPaidWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID uuid,
            @Valid @RequestBody AdminMarkPaidWithdrawalRequestDTO request) {
        return ok(withdrawalRequestService.markPaidWithdrawalRequestForAdmin(userDetails.getUser(), uuid, request));
    }

    @GetMapping("/commission-policies")
    @Operation(summary = "Xem danh sách chính sách hoa hồng", description = "Lịch sử các phiên bản chính sách hoa hồng toàn hệ thống")
    public ResponseEntity<ApiResponse<List<CommissionPolicyResponseDTO>>> getCommissionPolicies() {
        List<CommissionPolicyResponseDTO> dtos = commissionPolicyService.getPolicies().stream()
                .map(this::mapToCommissionPolicyDTO)
                .toList();
        return ok(dtos);
    }

    @PostMapping("/commission-policies")
    @Operation(summary = "Tạo phiên bản chính sách hoa hồng mới", description = "Thiết lập mức hoa hồng (basis points: 1000 = 10%) áp dụng từ thời điểm effectiveAt")
    public ResponseEntity<ApiResponse<CommissionPolicyResponseDTO>> createCommissionPolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AdminCreateCommissionPolicyRequestDTO request) {
        CommissionPolicy policy = commissionPolicyService.createPolicy(
                request.getRateBasisPoints(),
                request.getEffectiveAt(),
                userDetails.getUser());
        return ok(mapToCommissionPolicyDTO(policy));
    }

    private CommissionPolicyResponseDTO mapToCommissionPolicyDTO(CommissionPolicy policy) {
        if (policy == null)
            return null;
        return CommissionPolicyResponseDTO.builder()
                .id(policy.getId())
                .rateBasisPoints(policy.getRateBasisPoints())
                .effectiveAt(policy.getEffectiveAt())
                .createdAt(policy.getCreatedAt())
                .createdByUserId(policy.getCreatedBy() != null ? policy.getCreatedBy().getId() : null)
                .createdByEmail(policy.getCreatedBy() != null ? policy.getCreatedBy().getEmail() : null)
                .build();
    }

    @PostMapping("/wallet-payments/{paymentId}/reverse")
    @Operation(summary = "Đảo giao dịch doanh thu pending", description = "Đảo khoản tiền pending của payment chưa release với lý do bắt buộc")
    public ResponseEntity<ApiResponse<Map<String, String>>> reversePendingPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("paymentId") Integer paymentId,
            @Valid @RequestBody AdminReversalRequestDTO request) {
        walletDomainService.reversePendingPayment(
                paymentId,
                request.getReason(),
                userDetails.getUser());
        return ok(Map.of("message", "Đảo giao dịch doanh thu thành công"));
    }

    @GetMapping("/wallet-reconciliation/integrity-check")
    @Operation(summary = "Đối soát toàn bộ ví với sổ cái", description = "So sánh snapshot ví hiện tại với tổng sổ cái append-only")
    public ResponseEntity<ApiResponse<List<ReconciliationReportDTO>>> integrityCheck() {
        return ok(reconciliationService.reconcileAllWallets());
    }
}
