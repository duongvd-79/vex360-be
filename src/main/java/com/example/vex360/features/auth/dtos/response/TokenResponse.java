package com.example.vex360.features.auth.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    @JsonIgnore
    private boolean rememberMe;
    @Builder.Default
    private String tokenType = "Bearer";
}
