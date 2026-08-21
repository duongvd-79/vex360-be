package com.example.vex360.features.wallet;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.wallet.controllers.AdminWalletController;
import com.example.vex360.features.wallet.entities.WithdrawalRequest;
import com.example.vex360.features.wallet.repositories.WithdrawalRequestRepository;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class WithdrawalAccountAccessTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @InjectMocks
    private WithdrawalRequestService withdrawalRequestService;

    private Company company;
    private UUID withdrawalUuid;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(UUID.randomUUID()).name("Test Organizer").build();
        withdrawalUuid = UUID.randomUUID();
    }

    @Test
    void getFullWithdrawalAccount_ReturnsSnapshotDetails_AndIncludesNoStoreHeader() {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .uuid(withdrawalUuid)
                .company(company)
                .accountNumberSnapshot("999988887777")
                .build();

        when(withdrawalRequestRepository.findByUuid(withdrawalUuid)).thenReturn(Optional.of(request));

        String accountNum = withdrawalRequestService.getFullWithdrawalAccountNumberForAdmin(withdrawalUuid);
        assertEquals("999988887777", accountNum);

        AdminWalletController controller = new AdminWalletController(
                null, withdrawalRequestService, null, null, null);

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller
                .getFullWithdrawalAccount(withdrawalUuid);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("999988887777", response.getBody().data().get("accountNumber"));
    }

    @Test
    void getFullWithdrawalAccountThrowsWhenRequestNotFound() {
        when(withdrawalRequestRepository.findByUuid(withdrawalUuid)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> withdrawalRequestService.getFullWithdrawalAccountNumberForAdmin(withdrawalUuid));

        assertSame(ErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND, exception.getErrorCode());
    }
}
