package com.example.vex360.features.product.dtos.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.example.vex360.shared.enums.StorageProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductContentPreUploadedRequest {
    @NotBlank(message = "URL nội dung không được để trống")
    private String contentUrl;

    @NotBlank(message = "Định danh tệp trên kho lưu trữ không được để trống")
    private String publicId;

    /** Null hiểu là CLOUDINARY để giữ tương thích với frontend chưa khai trường này. */
    private StorageProvider storageProvider;

    @NotBlank(message = "Loại file không được để trống")
    private String mimeType;

    private long fileSize;

    @NotNull(message = "Thứ tự nội dung không được để trống")
    @Min(0)
    private Integer orderIndex;
}
