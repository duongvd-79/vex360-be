package com.example.vex360.shared.exceptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.vex360.shared.utils.LogSanitizer;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // 1. Business Exceptions (thrown by developers)
        @ExceptionHandler(AppException.class)
        public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
                ErrorCode errorCode = ex.getErrorCode();
                log.warn("Business exception occurred: [{}] - {}", 
                                LogSanitizer.sanitize(errorCode.getCode()), 
                                LogSanitizer.sanitize(errorCode.getMessage()));

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }

        // 2. Validation Exceptions (When @Valid in Controller fails)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
                log.warn("Validation failed for request: {}", LogSanitizer.sanitize(request.getRequestURI()));

                List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                                .map(e -> ErrorResponse.ValidationError.builder()
                                                .field(e.getField())
                                                .message(e.getDefaultMessage())
                                                .build())
                                .collect(Collectors.toList());

                ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .validationErrors(validationErrors)
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }

        // 3. Request body parsing exceptions (invalid JSON or enum values)
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {
                log.warn("Request body is not readable for request: {}", LogSanitizer.sanitize(request.getRequestURI()));

                ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }

        // 4. Runtime Exceptions (e.g. Database Exception, NullPointer, etc.)
        // Prevent information disclosure by returning a generic uncaught exception error and logging stack trace
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
                log.error("Unhandled runtime exception occurred at {}: ", LogSanitizer.sanitize(request.getRequestURI()), ex);

                ErrorCode errorCode = ErrorCode.UNCATCHED_EXCEPTION;
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }

        // 5. Spring Security AccessDeniedException
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
                log.warn("Access denied for request: {}", LogSanitizer.sanitize(request.getRequestURI()));
                ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }

        // 6. Other Exceptions
        // Log full stack trace and return standard uncaught exception response
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
                log.error("System encountered an unexpected error at {}: ", LogSanitizer.sanitize(request.getRequestURI()), ex);

                ErrorCode errorCode = ErrorCode.UNCATCHED_EXCEPTION;
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(errorCode.getHttpStatus().value())
                                .error(errorCode.getHttpStatus().name())
                                .code(errorCode.getCode())
                                .message(errorCode.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
        }
}
