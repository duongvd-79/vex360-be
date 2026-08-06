package com.example.vex360.features.booth.dtos;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotCornersDTO {
    @Size(min = 3, max = 3, message = "Góc trên trái phải có đúng 3 tọa độ.")
    private List<@NotNull(message = "Tọa độ góc trên trái không được để trống.") Double> tl;

    @Size(min = 3, max = 3, message = "Góc trên phải phải có đúng 3 tọa độ.")
    private List<@NotNull(message = "Tọa độ góc trên phải không được để trống.") Double> tr;

    @Size(min = 3, max = 3, message = "Góc dưới trái phải có đúng 3 tọa độ.")
    private List<@NotNull(message = "Tọa độ góc dưới trái không được để trống.") Double> bl;

    @Size(min = 3, max = 3, message = "Góc dưới phải phải có đúng 3 tọa độ.")
    private List<@NotNull(message = "Tọa độ góc dưới phải không được để trống.") Double> br;
}
