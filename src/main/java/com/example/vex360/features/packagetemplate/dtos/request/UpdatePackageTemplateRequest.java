package com.example.vex360.features.packagetemplate.dtos.request;

import java.math.BigDecimal;

import com.example.vex360.shared.enums.BoothListingPriority;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePackageTemplateRequest {
    @NotBlank(message = "Tên gói không được để trống")
    private String name;

    @NotBlank(message = "Mô tả gói không được để trống")
    private String description;

    @NotNull(message = "Giá gói không được để trống")
    @DecimalMin(value = "0.00", message = "Giá gói phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    private String currency;

    @NotNull(message = "Số sản phẩm tối đa mỗi gian hàng không được để trống")
    @Min(value = 1, message = "Số sản phẩm tối đa mỗi gian hàng phải lớn hơn hoặc bằng 1")
    private Integer maxProductsPerBooth;

    @NotNull(message = "Số video nhúng tối đa mỗi gian hàng không được để trống")
    @Min(value = 1, message = "Số video nhúng tối đa mỗi gian hàng phải lớn hơn hoặc bằng 1")
    private Integer maxEmbeddedVideosPerBooth;

    @NotNull(message = "Số panorama tối đa mỗi gian hàng không được để trống")
    @Min(value = 1, message = "Số panorama tối đa mỗi gian hàng phải lớn hơn hoặc bằng 1")
    private Integer maxPanoramasPerBooth;

    @NotNull(message = "Số hotspot tối đa mỗi gian hàng không được để trống")
    @Min(value = 1, message = "Số hotspot tối đa mỗi gian hàng phải lớn hơn hoặc bằng 1")
    private Integer maxHotspotsPerBooth;

    @NotNull(message = "Dung lượng lưu trữ không được để trống")
    @Min(value = 1, message = "Dung lượng lưu trữ phải lớn hơn hoặc bằng 1")
    private Long storageLimitMb;

    @NotNull(message = "Vị trí hiển thị không được để trống")
    private BoothListingPriority listingPriority;
}
