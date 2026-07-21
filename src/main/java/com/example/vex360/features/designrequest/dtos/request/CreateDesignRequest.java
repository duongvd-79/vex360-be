package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDesignRequest {
    @NotNull(message = "Booth id không được để trống")
    private UUID boothId;

    private List<UUID> productIds;

    private String note;

    public CreateDesignRequest(UUID boothId, String note) {
        this(boothId, List.of(), note);
    }
}
