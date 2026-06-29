package com.example.vex360.features.partnership;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.vex360.features.partnership.controllers.PartnershipRequestController;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PartnershipRequestControllerUnitTest {
    @Mock
    private PartnershipRequestService partnershipRequestService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID requestId;
    private User user;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PartnershipRequestController(partnershipRequestService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        requestId = UUID.randomUUID();
        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .role(Role.VISITOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitGuestRequestReturnsCreatedApiResponse() throws Exception {
        SubmitPartnershipRequest request = validRequest("guest@example.com", Role.EXHIBITOR);
        PartnershipRequestResponseDTO response = response("guest@example.com", null, Role.EXHIBITOR);

        when(partnershipRequestService.submitGuestRequest(any(SubmitPartnershipRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/partnership-requests/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requesterEmail").value("guest@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void submitAuthenticatedRequestReturnsCreatedApiResponse() throws Exception {
        SubmitPartnershipRequest request = validRequest("user@example.com", Role.ORGANIZER);
        PartnershipRequestResponseDTO response = response("user@example.com", user.getId(), Role.ORGANIZER);
        authenticate(user);

        when(partnershipRequestService.submitAuthenticatedRequest(eq(user), any(SubmitPartnershipRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/partnership-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submittedByUserId").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.requestedRole").value("ORGANIZER"));
    }

    @Test
    void submitGuestRequestRejectsInvalidFieldsBeforeService() throws Exception {
        SubmitPartnershipRequest request = new SubmitPartnershipRequest(
                "",
                "invalid-email",
                "123",
                "",
                null,
                "Optional message",
                false);

        mockMvc.perform(post("/api/v1/partnership-requests/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[*].field",
                        hasItems("requesterName", "requesterEmail", "requesterPhoneNumber",
                                "organizationName", "requestedRole", "acceptedPolicy")));

        verify(partnershipRequestService, never()).submitGuestRequest(any());
    }

    @Test
    void submitAuthenticatedRequestRejectsInvalidFieldsBeforeService() throws Exception {
        SubmitPartnershipRequest request = new SubmitPartnershipRequest(
                "",
                "invalid-email",
                "123",
                "",
                null,
                "Optional message",
                false);
        authenticate(user);

        mockMvc.perform(post("/api/v1/partnership-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[*].field",
                        hasItems("requesterName", "requesterEmail", "requesterPhoneNumber",
                                "organizationName", "requestedRole", "acceptedPolicy")));

        verify(partnershipRequestService, never()).submitAuthenticatedRequest(any(), any());
    }

    private void authenticate(User authenticatedUser) {
        CustomUserDetails userDetails = new CustomUserDetails(authenticatedUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private SubmitPartnershipRequest validRequest(String email, Role role) {
        return new SubmitPartnershipRequest(
                "Requester Name",
                email,
                "0912345678",
                "Vex360 Partner",
                role,
                "We want to partner",
                true);
    }

    private PartnershipRequestResponseDTO response(String email, UUID submittedByUserId, Role role) {
        return new PartnershipRequestResponseDTO(
                requestId,
                submittedByUserId,
                submittedByUserId == null ? null : "user@example.com",
                "Requester Name",
                email,
                "0912345678",
                "Vex360 Partner",
                role.name(),
                "We want to partner",
                true,
                "PENDING",
                null,
                null,
                null);
    }
}
