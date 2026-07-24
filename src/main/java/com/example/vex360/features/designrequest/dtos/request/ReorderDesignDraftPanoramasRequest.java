package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderDesignDraftPanoramasRequest {
    @NotEmpty(message = "Danh sach panorama khong duoc de trong")
    private List<@NotNull UUID> panoramaIds;
}
