package com.example.vex360.features.designrequest.dtos.request;

import java.util.List;
import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignRequestScope;

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

    @NotNull(message = "Phạm vi thiết kế không được để trống")
    private DesignRequestScope scope;

    private List<UUID> productIds;

    private String note;

    public CreateDesignRequest(UUID boothId, String note) {
        this(boothId, DesignRequestScope.FULL, List.of(), note);
    }
}
