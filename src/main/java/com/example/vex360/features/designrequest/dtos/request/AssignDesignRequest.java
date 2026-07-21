package com.example.vex360.features.designrequest.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignDesignRequest {
    @NotNull(message = "Designer id không được để trống")
    private UUID designerId;
}
