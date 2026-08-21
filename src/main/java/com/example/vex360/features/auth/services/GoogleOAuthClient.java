package com.example.vex360.features.auth.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

/**
 * Exchanges Google OAuth authorization codes for validated user profile data.
 * HTTP transport configuration is supplied by the dedicated Google
 * {@link RestTemplate} bean.
 */
@Service
public class GoogleOAuthClient {

    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String userInfoUri;

    public GoogleOAuthClient(
            @Qualifier("googleRestTemplate") RestTemplate restTemplate,
            @Value("${app.security.oauth2.client-id}") String clientId,
            @Value("${app.security.oauth2.client-secret}") String clientSecret,
            @Value("${app.security.oauth2.redirect-uri}") String redirectUri,
            @Value("${app.security.oauth2.user-info-uri}") String userInfoUri) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.userInfoUri = userInfoUri;
    }

    /**
     * Exchanges an authorization code for a Google access token and retrieves the
     * associated profile.
     *
     * @param code authorization code returned by Google
     * @return validated Google profile containing at least an email address
     * @throws AppException when Google does not return a usable token or email
     */
    public GoogleProfile exchangeCode(String code) {
        String accessToken = exchangeAccessToken(code);
        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        Object email = userInfo.get("email");
        if (!(email instanceof String emailValue) || emailValue.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return new GoogleProfile(
                emailValue,
                stringValue(userInfo.get("name")),
                stringValue(userInfo.get("picture")));
    }

    private String exchangeAccessToken(String code) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("code", code);
        parameters.add("client_id", clientId);
        parameters.add("client_secret", clientSecret);
        parameters.add("redirect_uri", redirectUri);
        parameters.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                GOOGLE_TOKEN_URI,
                new HttpEntity<>(parameters, headers),
                Map.class);

        Object accessToken = response == null ? null : response.get("access_token");
        if (!(accessToken instanceof String tokenValue) || tokenValue.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return tokenValue;
    }

    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                userInfoUri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        if (response.getBody() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = response.getBody();
        return userInfo;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    /**
     * Immutable subset of Google profile data required by the auth module.
     *
     * @param email     verified account email
     * @param fullName  display name, possibly {@code null}
     * @param avatarUrl profile image URL, possibly {@code null}
     */
    public record GoogleProfile(String email, String fullName, String avatarUrl) {
    }
}
