package com.example.vex360.shared.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.vex360.shared.dtos.ApiResponse;

public abstract class BaseController {
    protected <T> ApiResponse<T> createSuccessResponse(T data) {
        return ApiResponse.success(data);
    }

    protected <T> ApiResponse<T> createSuccessResponse(T data, String message) {
        return ApiResponse.success(data, message);
    }

    protected <T> ApiResponse<T> createErrorResponse(int code, String message) {
        return ApiResponse.error(code, message);
    }

    protected <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    protected <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    protected <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    protected <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, message));
    }
}
