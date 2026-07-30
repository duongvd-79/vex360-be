package com.example.vex360.features.wallet.controllers;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.wallet.dtos.CompanyPayoutProfileResponseDTO;
import com.example.vex360.features.wallet.dtos.CreateWithdrawalRequestDTO;
import com.example.vex360.features.wallet.dtos.ExhibitionWalletSummaryDTO;
import com.example.vex360.features.wallet.dtos.OrganizerWalletResponseDTO;
import com.example.vex360.features.wallet.dtos.UpdatePayoutProfileRequestDTO;
import com.example.vex360.features.wallet.dtos.WalletTransactionResponseDTO;
import com.example.vex360.features.wallet.dtos.WithdrawalRequestResponseDTO;
import com.example.vex360.features.wallet.enums.WalletTransactionType;
import com.example.vex360.features.wallet.services.CompanyPayoutProfileService;
import com.example.vex360.features.wallet.services.OrganizerWalletService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/api/v1/organizer")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAuthority('ORGANIZER')")
@RequireActiveCompany(roles = Role.ORGANIZER)
@Tag(name = "Organizer Wallet & Withdrawals", description = "API quản lý ví doanh thu và yêu cầu rút tiền cho Organizer")
public class OrganizerWalletController extends BaseController {

    OrganizerWalletService organizerWalletService;
    CompanyPayoutProfileService payoutProfileService;
    WithdrawalRequestService withdrawalRequestService;

    @GetMapping("/wallet")
    @Operation(summary = "Xem tổng quan ví doanh thu", description = "Trả về số dư pending, available, reserved, tổng đã rút và thông tin cấu hình rút tiền của doanh nghiệp")
    public ResponseEntity<ApiResponse<OrganizerWalletResponseDTO>> getWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ok(organizerWalletService.getWalletForOrganizer(userDetails.getUser()));
    }

    @GetMapping("/wallet/transactions")
    @Operation(summary = "Xem lịch sử giao dịch ví", description = "Danh sách sổ cái append-only có phân trang và lọc theo exhibition/loại giao dịch")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponseDTO>>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "exhibitionUuid", required = false) UUID exhibitionUuid,
            @RequestParam(name = "type", required = false) WalletTransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(organizerWalletService.getTransactionsForOrganizer(userDetails.getUser(), exhibitionUuid, type,
                pageable));
    }

    @GetMapping("/wallet/exhibitions")
    @Operation(summary = "Xem tổng hợp doanh thu theo triển lãm", description = "Thống kê tổng số đơn hàng đã thanh toán, gross, system fee, net, pending và available theo triển lãm")
    public ResponseEntity<ApiResponse<PageResponse<ExhibitionWalletSummaryDTO>>> getExhibitionSummaries(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ok(organizerWalletService.getExhibitionSummariesForOrganizer(userDetails.getUser(), pageable));
    }

    @GetMapping("/payout-profile")
    @Operation(summary = "Xem thông tin tài khoản nhận tiền", description = "Trả về thông tin ngân hàng đã mask số tài khoản")
    public ResponseEntity<ApiResponse<CompanyPayoutProfileResponseDTO>> getPayoutProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ok(payoutProfileService.getProfileForOrganizer(userDetails.getUser()));
    }

    @PutMapping("/payout-profile")
    @Operation(summary = "Cập nhật thông tin tài khoản nhận tiền", description = "Cập nhật thông tin ngân hàng. Thao tác này đưa hồ sơ về trạng thái PENDING_VERIFICATION cần Admin duyệt")
    public ResponseEntity<ApiResponse<CompanyPayoutProfileResponseDTO>> updatePayoutProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdatePayoutProfileRequestDTO request) {
        return ok(payoutProfileService.updateProfileForOrganizer(userDetails.getUser(), request));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Tạo yêu cầu rút tiền mới", description = "Tạo yêu cầu rút tiền từ số dư availableBalance (tối thiểu 100.000 VND). Số tiền sẽ được đưa vào reservedBalance")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> createWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateWithdrawalRequestDTO request) {
        return ok(withdrawalRequestService.createWithdrawalRequest(userDetails.getUser(), request));
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Xem danh sách yêu cầu rút tiền", description = "Lịch sử các yêu cầu rút tiền của doanh nghiệp")
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalRequestResponseDTO>>> getWithdrawals(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ok(withdrawalRequestService.getWithdrawalRequestsForOrganizer(userDetails.getUser(), pageable));
    }

    @GetMapping("/withdrawals/{uuid}")
    @Operation(summary = "Xem chi tiết một yêu cầu rút tiền", description = "Thông tin chi tiết đơn rút tiền theo UUID")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> getWithdrawalDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID uuid) {
        return ok(withdrawalRequestService.getWithdrawalRequestDetailsForOrganizer(userDetails.getUser(), uuid));
    }

    @PostMapping("/withdrawals/{uuid}/cancel")
    @Operation(summary = "Hủy yêu cầu rút tiền", description = "Organizer chỉ có thể hủy yêu cầu ở trạng thái PENDING. Số tiền đã giữ sẽ được hoàn lại vào availableBalance")
    public ResponseEntity<ApiResponse<WithdrawalRequestResponseDTO>> cancelWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("uuid") UUID uuid) {
        return ok(withdrawalRequestService.cancelWithdrawalRequestForOrganizer(userDetails.getUser(), uuid));
    }
}
