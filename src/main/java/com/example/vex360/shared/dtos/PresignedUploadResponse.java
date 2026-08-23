package com.example.vex360.shared.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Các vé tải lên đã ký, mỗi tệp một vé, tất cả nằm chung một thư mục. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {

    /** Thư mục chung của cả lô, dạng "products/{companyId}/{uuid}/". */
    private String prefix;

    private List<Item> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        private String fileName;

        /** prefix + fileName, là khoá định danh của object trên R2. */
        private String objectKey;

        /** URL đã ký, trình duyệt PUT thẳng nội dung tệp vào đây. */
        private String uploadUrl;

        /** Trình duyệt phải gửi đúng chuỗi này ở header Content-Type, lệch là hỏng chữ ký. */
        private String contentType;

        /** Địa chỉ đọc công khai, dùng để hiển thị lại sau khi đã lưu vào cơ sở dữ liệu. */
        private String publicUrl;
    }
}
