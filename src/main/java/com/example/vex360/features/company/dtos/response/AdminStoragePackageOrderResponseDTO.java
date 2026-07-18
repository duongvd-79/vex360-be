package com.example.vex360.features.company.dtos.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStoragePackageOrderResponseDTO {
    Integer id;
    Long orderCode;
    String companyName; // tên doanh nghiệp
    String packageName; // tên gói đã mua
    Long quotaBytes; // dung lượng của gói
    Long amountVnd; // số tiền
    String status; // trạng thái thanh toán (PENDING/PAID/...)
    LocalDateTime paidAt; // thời điểm thanh toán (null nếu chưa trả)
    LocalDateTime createdAt; // thời điểm tạo đơn
}
