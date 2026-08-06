package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
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
    private List<@NotNull(message = "ID panorama không được để trống.") UUID> panoramaIds;

    @JsonIgnore
    @AssertTrue(message = "Danh sách panorama không được chứa ID trùng lặp.")
    public boolean isPanoramaIdsUnique() {
        return panoramaIds == null || new HashSet<>(panoramaIds).size() == panoramaIds.size();
    }
}
