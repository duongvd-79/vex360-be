package com.example.vex360.features.booth.dtos.request;

import java.util.UUID;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBoothTemplateHotspotRequest {
    @Pattern(regexp = "^(?=.*\\S).+$", message = "Tên hotspot không được để trống nếu được cung cấp.")
    @Size(max = 255, message = "Tên hotspot không được vượt quá 255 ký tự.")
    private String name;
    private UUID targetPanoramaId;
    private Double xPosition;
    private Double yPosition;
    private Double zPosition;
}
