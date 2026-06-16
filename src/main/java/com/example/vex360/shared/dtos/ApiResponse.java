package com.example.vex360.shared.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Builder.Default
    private boolean success = true;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private int status;          // Ví dụ: 200, 201
    private String code;         // Business Code (Ví dụ: "SUCCESS")
    private String message;      // Thông điệp trả về cho người dùng
    private T data;              // Dữ liệu payload khi thành công

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .code("SUCCESS")
                .message("Thành công")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(int status, T data, String message) {
        return ApiResponse.<T>builder()
                .status(status)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }
}
