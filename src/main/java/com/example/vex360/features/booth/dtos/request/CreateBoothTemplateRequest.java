package com.example.vex360.features.booth.dtos.request;

import java.util.List;

import com.example.vex360.features.booth.enums.BoothStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoothTemplateRequest {
    @NotBlank(message = "Ten booth khong duoc de trong")
    private String name;

    private String description;

    private BoothStatus status;

    @NotEmpty(message = "Booth template can it nhat 1 panorama")
    private List<@Valid CreatePanoramaRequest> panoramas;
}
