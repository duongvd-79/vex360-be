package com.example.vex360.shared.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu dọn một thư mục tải lên chưa gắn vào sản phẩm nào, khi người dùng huỷ biểu mẫu. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteR2FolderRequest {

    /** Đúng chuỗi prefix mà endpoint cấp vé đã trả về, kèm dấu gạch chéo cuối. */
    @NotBlank(message = "Thư mục cần xoá không được để trống")
    private String prefix;
}
