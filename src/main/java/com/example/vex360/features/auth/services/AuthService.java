package com.example.vex360.features.auth.services;

import com.example.vex360.features.auth.dtos.request.LoginRequest;
import com.example.vex360.features.auth.dtos.response.TokenResponse;

public interface AuthService {
    TokenResponse login(LoginRequest request);

    TokenResponse loginWithGoogle(String code);

    TokenResponse refreshToken(String refreshToken);

    void logout(String refreshToken, String accessToken);
}
