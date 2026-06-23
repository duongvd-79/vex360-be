package com.example.vex360.features.company;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItems;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.company.controllers.CompanyController;
import com.example.vex360.features.company.dtos.request.UpdateCompanyProfileRequest;
import com.example.vex360.features.company.dtos.response.CompanyResponseDTO;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CompanyControllerUnitTest {
    @Mock
    private CompanyService companyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User user;
    private CompanyResponseDTO response;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CompanyController(companyService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.EXHIBITOR)
                .status(UserStatus.ACTIVE)
                .build();
        response = new CompanyResponseDTO(
                UUID.randomUUID(),
                user.getId(),
                "Company A",
                "Technology",
                "Company description",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "user@example.com",
                "0912345678",
                "123 Main St",
                "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserCompanyReturnsApiResponse() throws Exception {
        authenticate(user);
        when(companyService.getCurrentUserCompany(user)).thenReturn(response);

        mockMvc.perform(get("/api/v1/companies/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void updateCurrentUserCompanyReturnsApiResponse() throws Exception {
        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "Technology",
                "Company description",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "0912345678",
                "123 Main St");
        authenticate(user);

        when(companyService.updateCurrentUserCompany(eq(user), any(UpdateCompanyProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/companies/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("Technology"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void updateCurrentUserCompanyRejectsInvalidPhoneBeforeService() throws Exception {
        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                "Technology",
                "Company description",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "123",
                "123 Main St");
        authenticate(user);

        mockMvc.perform(patch("/api/v1/companies/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItems("phone")));

        verify(companyService, never()).updateCurrentUserCompany(any(), any());
    }

    @Test
    void updateCurrentUserCompanyRejectsBlankRequiredFieldsBeforeService() throws Exception {
        UpdateCompanyProfileRequest request = new UpdateCompanyProfileRequest(
                " ",
                "",
                "https://cdn.example.com/logo.png",
                "https://example.com",
                "0912345678",
                " ");
        authenticate(user);

        mockMvc.perform(patch("/api/v1/companies/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[*].field",
                        hasItems("industry", "description", "address")));

        verify(companyService, never()).updateCurrentUserCompany(any(), any());
    }

    @Test
    void updateCurrentUserCompanyRejectsMissingRequiredFieldsBeforeService() throws Exception {
        authenticate(user);

        mockMvc.perform(patch("/api/v1/companies/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "logoUrl": "https://cdn.example.com/logo.png",
                          "website": "https://example.com",
                          "phone": "0912345678"
                        }
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[*].field",
                        hasItems("industry", "description", "address")));

        verify(companyService, never()).updateCurrentUserCompany(any(), any());
    }

    private void authenticate(User authenticatedUser) {
        CustomUserDetails userDetails = new CustomUserDetails(authenticatedUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
