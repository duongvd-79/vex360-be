package com.example.vex360.features.company.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageUsageResponseDTO {
    Long usedBytes;
    Long reservedBytes;
    Long quotaBytes;
    Long availableBytes;
    Double usedPercentage;

    /** Phần dung lượng do tệp media (ảnh/video dùng chung) chiếm. */
    Long mediaAssetUsedBytes;
    /** Phần còn lại của usedBytes: ảnh đại diện và nội dung của sản phẩm. */
    Long productUsedBytes;
}
