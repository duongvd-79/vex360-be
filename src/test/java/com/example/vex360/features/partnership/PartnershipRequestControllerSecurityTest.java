package com.example.vex360.features.partnership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.partnership.controllers.PartnershipRequestController;
import com.example.vex360.features.partnership.dtos.request.SubmitPartnershipRequest;
import com.example.vex360.features.partnership.dtos.response.PartnershipRequestResponseDTO;
import com.example.vex360.features.partnership.services.PartnershipRequestService;
import com.example.vex360.shared.dtos.ApiResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;

@SpringJUnitConfig(PartnershipRequestControllerSecurityTest.TestConfig.class)
class PartnershipRequestControllerSecurityTest {

    @Autowired
    private PartnershipRequestController controller;

    @Autowired
    private PartnershipRequestService partnershipRequestService;

    @BeforeEach
    void setup() {
        reset(partnershipRequestService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void visitorCanSubmitAuthenticatedPartnershipRequest() {
        CustomUserDetails userDetails = authenticate(Role.VISITOR);
        SubmitPartnershipRequest request = validRequest();
        PartnershipRequestResponseDTO response = response(userDetails.getUser().getId());

        when(partnershipRequestService.submitAuthenticatedRequest(any(User.class), any(SubmitPartnershipRequest.class)))
                .thenReturn(response);

        ResponseEntity<ApiResponse<PartnershipRequestResponseDTO>> result = controller.submitAuthenticatedRequest(
                userDetails,
                request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(userDetails.getUser().getId(), result.getBody().data().getSubmittedByUserId());
        verify(partnershipRequestService).submitAuthenticatedRequest(any(User.class), any(SubmitPartnershipRequest.class));
    }

    @Test
    void nonVisitorRolesCannotSubmitAuthenticatedPartnershipRequest() {
        for (Role role : List.of(Role.ADMIN, Role.EXHIBITOR, Role.ORGANIZER)) {
            CustomUserDetails userDetails = authenticate(role);

            assertThrows(AccessDeniedException.class,
                    () -> controller.submitAuthenticatedRequest(userDetails, validRequest()));
        }

        verify(partnershipRequestService, never())
                .submitAuthenticatedRequest(any(User.class), any(SubmitPartnershipRequest.class));
    }

    private CustomUserDetails authenticate(Role role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@example.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        return userDetails;
    }

    private SubmitPartnershipRequest validRequest() {
        return new SubmitPartnershipRequest(
                "Requester Name",
                "user@example.com",
                "0912345678",
                "Vex360 Partner",
                Role.EXHIBITOR,
                "We want to partner",
                true);
    }

    private PartnershipRequestResponseDTO response(UUID submittedByUserId) {
        return new PartnershipRequestResponseDTO(
                UUID.randomUUID(),
                submittedByUserId,
                "user@example.com",
                "Requester Name",
                "user@example.com",
                "0912345678",
                "Vex360 Partner",
                Role.EXHIBITOR.name(),
                "We want to partner",
                true,
                "PENDING",
                null,
                null,
                null);
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        PartnershipRequestService partnershipRequestService() {
            return mock(PartnershipRequestService.class);
        }

        @Bean
        PartnershipRequestController partnershipRequestController(
                PartnershipRequestService partnershipRequestService) {
            return new PartnershipRequestController(partnershipRequestService);
        }
    }
}
