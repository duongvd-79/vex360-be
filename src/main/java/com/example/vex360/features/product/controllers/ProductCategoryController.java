package com.example.vex360.features.product.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.product.dtos.request.CreateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryStatusRequest;
import com.example.vex360.features.product.dtos.response.ProductCategoryResponseDTO;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.services.ProductCategoryService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController extends BaseController {
    private final ProductCategoryService productCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductCategoryResponseDTO>>> getCategories(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ProductCategoryStatus status) {
        List<ProductCategoryResponseDTO> categories = productCategoryService.getCategories(userDetails.getUser(), status);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(categories));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCategoryResponseDTO>> createCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateProductCategoryRequest request) {
        ProductCategoryResponseDTO category = productCategoryService.createCategory(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createSuccessResponse(category));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCategoryResponseDTO>> updateCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductCategoryRequest request) {
        ProductCategoryResponseDTO category = productCategoryService.updateCategory(userDetails.getUser(), id, request);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(category));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductCategoryResponseDTO>> updateCategoryStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductCategoryStatusRequest request) {
        ProductCategoryResponseDTO category = productCategoryService.updateCategoryStatus(userDetails.getUser(), id, request);
        return ResponseEntity.status(HttpStatus.OK).body(createSuccessResponse(category));
    }
}
