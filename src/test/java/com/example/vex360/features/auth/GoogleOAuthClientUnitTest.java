package com.example.vex360.features.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import com.example.vex360.features.auth.services.GoogleOAuthClient;
import com.example.vex360.features.auth.services.GoogleOAuthClient.GoogleProfile;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthClientUnitTest {

    @Mock
    private RestTemplate restTemplate;

    private GoogleOAuthClient googleOAuthClient;
    private final String userInfoUri = "https://www.googleapis.com/oauth2/v2/userinfo";

    @BeforeEach
    void setUp() {
        googleOAuthClient = new GoogleOAuthClient(
                restTemplate,
                "client_id",
                "client_secret",
                "redirect_uri",
                userInfoUri
        );
    }

    @Test
    void exchangeCode_Success() {
        Map<String, Object> tokenResponse = Map.of("access_token", "google_access_token");
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResponse);

        Map<String, Object> userInfoResponse = Map.of(
                "email", "user@gmail.com",
                "name", "Google Name",
                "picture", "https://avatar.url"
        );
        when(restTemplate.exchange(eq(userInfoUri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userInfoResponse));

        GoogleProfile profile = googleOAuthClient.exchangeCode("code123");

        assertNotNull(profile);
        assertEquals("user@gmail.com", profile.email());
        assertEquals("Google Name", profile.fullName());
        assertEquals("https://avatar.url", profile.avatarUrl());
    }

    @Test
    void exchangeCode_NullOrBlankAccessToken_ThrowsAppException() {
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        AppException ex = assertThrows(AppException.class, () -> googleOAuthClient.exchangeCode("code123"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void exchangeCode_NullUserInfoResponseBody_ThrowsAppException() {
        Map<String, Object> tokenResponse = Map.of("access_token", "google_access_token");
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResponse);

        when(restTemplate.exchange(eq(userInfoUri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null));

        AppException ex = assertThrows(AppException.class, () -> googleOAuthClient.exchangeCode("code123"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void exchangeCode_BlankOrMissingEmail_ThrowsAppException() {
        Map<String, Object> tokenResponse = Map.of("access_token", "google_access_token");
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResponse);

        Map<String, Object> userInfoResponse = Map.of("name", "No Email User");
        when(restTemplate.exchange(eq(userInfoUri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userInfoResponse));

        AppException ex = assertThrows(AppException.class, () -> googleOAuthClient.exchangeCode("code123"));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void exchangeCode_NonStringFullNameAndPicture_ReturnsNullForOptionalFields() {
        Map<String, Object> tokenResponse = Map.of("access_token", "google_access_token");
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(tokenResponse);

        Map<String, Object> userInfoResponse = Map.of(
                "email", "user@gmail.com",
                "name", 12345, // Not a string
                "picture", true // Not a string
        );
        when(restTemplate.exchange(eq(userInfoUri), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userInfoResponse));

        GoogleProfile profile = googleOAuthClient.exchangeCode("code123");

        assertNotNull(profile);
        assertEquals("user@gmail.com", profile.email());
        assertNull(profile.fullName());
        assertNull(profile.avatarUrl());
    }
}
