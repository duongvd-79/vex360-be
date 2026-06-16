package com.example.vex360.shared.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    @Test
    void commenceReturnsUnauthenticatedErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("Missing token"));

        JsonNode body = JsonMapper.builder().findAndAddModules().build()
                .readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("code").asText()).isEqualTo("AUTH-001");
        assertThat(body.get("message").asText()).isEqualTo("Unauthenticated or token expired");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users");
    }

    @Test
    void handleReturnsUnauthorizedErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Forbidden"));

        JsonNode body = JsonMapper.builder().findAndAddModules().build()
                .readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.get("code").asText()).isEqualTo("AUTH-002");
        assertThat(body.get("message").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users");
    }
}
