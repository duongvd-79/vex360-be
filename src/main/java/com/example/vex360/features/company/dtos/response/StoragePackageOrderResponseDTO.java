package com.example.vex360.features.company.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoragePackageOrderResponseDTO {
    Integer orderId;
    Long orderCode;
    String checkoutUrl;
    String packageName;
    Long quotaBytes;
    Long amountVnd;
    String status;
}
