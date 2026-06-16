package com.example.vex360.shared.controllers;

import com.example.vex360.shared.dtos.ApiResponse;

public abstract class BaseController {
    protected <T> ApiResponse<T> createSuccessResponse(T data) {
        return ApiResponse.success(data);
    }

    protected <T> ApiResponse<T> createErrorResponse(int code, String message) {
        return ApiResponse.error(code, message);
    }

}
