package com.example.vex360.shared.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.vex360.shared.dtos.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // 1. Bắt lỗi nghiệp vụ do chính Dev tự throw (AppException)
        @ExceptionHandler(AppException.class)
        public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
                ErrorCode errorCode = ex.getErrorCode();
                ApiResponse<Object> apiResponse = ApiResponse.error(errorCode.getHttpStatus().value(), errorCode.getMessage());
                return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
        }

        // 2. Bắt lỗi Validation (Khi @Valid ở Controller kích hoạt thất bại)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ApiResponse<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
                Map<String, String> errors = new HashMap<>();
                ex.getBindingResult().getFieldErrors().forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
                return ApiResponse.errorListDataMessages(400, "Validation Failed", errors);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
                ApiResponse<Object> apiResponse = ApiResponse.error(400, ex.getMessage());
                return ResponseEntity.badRequest().body(apiResponse);
        }

        // 3. Bắt các lỗi hệ thống còn lại (Lỗi DB, Lỗi Access Denied, Lỗi NullPointer...)
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
                ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
                ApiResponse<Object> apiResponse = ApiResponse.error(errorCode.getHttpStatus().value(), errorCode.getMessage());
                return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
                // Ghi Log lỗi chi tiết ở Server để Dev xem cứu hộ (Không trả log này về Client)
                log.error("Hệ thống gặp lỗi nghiêm trọng: ", ex);

                ErrorCode errorCode = ErrorCode.UNCATCHED_EXCEPTION;
                ApiResponse<Object> apiResponse = ApiResponse.error(errorCode.getHttpStatus().value(), errorCode.getMessage());
                return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
        }
}
