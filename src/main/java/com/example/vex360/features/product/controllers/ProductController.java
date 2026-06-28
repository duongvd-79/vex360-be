package com.example.vex360.features.product.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.shared.controllers.BaseController;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.dtos.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController extends BaseController {
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponseDTO>>> getProducts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt") Pageable pageable) {
        PageResponse<ProductResponseDTO> products = productService.getProducts(userDetails.getUser(), pageable);
        return ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        ProductResponseDTO product = productService.getProductById(userDetails.getUser(), id);
        return ok(product);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") CreateProductRequest request,
            @RequestPart("thumbnail") MultipartFile thumbnail,
            MultipartHttpServletRequest multipartRequest) {
        ProductResponseDTO product = productService.createProduct(
                userDetails.getUser(),
                request,
                thumbnail,
                extractContentFiles(multipartRequest));
        return created(product);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponseDTO>> updateProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestPart("metadata") UpdateProductRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            MultipartHttpServletRequest multipartRequest) {
        ProductResponseDTO product = productService.updateProduct(
                userDetails.getUser(),
                id,
                request,
                thumbnail,
                extractContentFiles(multipartRequest));
        return ok(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        ProductResponseDTO product = productService.deleteProduct(userDetails.getUser(), id);
        return ok(product);
    }

    private Map<String, MultipartFile> extractContentFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> files = new LinkedHashMap<>(request.getFileMap());
        files.remove("metadata");
        files.remove("thumbnail");
        return files;
    }
}
