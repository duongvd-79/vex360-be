package com.example.vex360.shared.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Danh sách tệp mà trình duyệt xin vé để tải thẳng lên R2. Không có trường contentType:
 * kiểu tệp do máy chủ tự suy từ phần mở rộng, vì client khai gì cũng được.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignUploadRequest {

    @NotEmpty(message = "Danh sách tệp không được để trống")
    @Size(max = 30, message = "Mỗi lượt chỉ được tải lên tối đa 30 tệp")
    @Valid
    private List<FileItem> files;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileItem {

        /** Giữ nguyên tên gốc: tệp .obj tìm .mtl và texture theo đúng tên này. */
        @NotBlank(message = "Tên tệp không được để trống")
        private String fileName;

        /**
         * Chỉ dùng để từ chối sớm khi đã chắc chắn vượt dung lượng cho phép. Không tin được
         * vì URL đã ký không ràng buộc kích thước thật; số thật lấy lại bằng HeadObject.
         */
        @Min(value = 1, message = "Dung lượng tệp phải lớn hơn 0")
        private long fileSize;
    }
}
