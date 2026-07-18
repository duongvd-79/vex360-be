package com.example.vex360.features.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        client = new GoogleOAuthClient(
                restTemplate,
                "client-id",
                "client-secret",
                "redirect-uri",
                "https://google.example/userinfo");
    }

    @Test
    void exchangeCode_ReturnsValidatedProfile() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", "google-access-token"));
        when(restTemplate.exchange(
                eq("https://google.example/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "email", "user@example.com",
                        "name", "User",
                        "picture", "avatar")));

        GoogleOAuthClient.GoogleProfile profile = client.exchangeCode("authorization-code");

        assertEquals("user@example.com", profile.email());
        assertEquals("User", profile.fullName());
        assertEquals("avatar", profile.avatarUrl());
    }

    @Test
    void exchangeCode_MissingAccessTokenIsRejected() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of());

        AppException exception = assertThrows(AppException.class, () -> client.exchangeCode("invalid-code"));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void exchangeCode_MissingEmailIsRejected() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", "google-access-token"));
        when(restTemplate.exchange(
                eq("https://google.example/userinfo"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("name", "User")));

        AppException exception = assertThrows(AppException.class, () -> client.exchangeCode("authorization-code"));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }
}
