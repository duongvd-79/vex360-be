package com.example.vex360.features.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.user.controllers.AdminUserController;
import com.example.vex360.features.user.controllers.CurrentUserController;
import com.example.vex360.features.user.dtos.request.ChangePasswordRequest;
import com.example.vex360.features.user.dtos.request.CreateUserRequest;
import com.example.vex360.features.user.dtos.request.UpdateProfileRequest;
import com.example.vex360.features.user.dtos.response.UserResponseDTO;
import com.example.vex360.features.user.dtos.response.UserSummaryResponseDTO;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserControllerUnitTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID userId;
    private UserResponseDTO response;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AdminUserController(userService),
                        new CurrentUserController(userService))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
        response = new UserResponseDTO(
                userId, "user@example.com", "User Name", "0912345678", "VISITOR", "avatar.png", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUserReturnsApiResponse() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "User Name", "0912345678", Role.VISITOR);

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void getUsersReturnsPagedApiResponse() throws Exception {
        PageResponse<UserResponseDTO> pageResponse = PageResponse.<UserResponseDTO>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(userService.getUsers(eq("user"), eq(Role.ADMIN), eq(UserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/users?page=0&size=10&keyword=user&role=ADMIN&status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getUserSummaryReturnsApiResponse() throws Exception {
        UserSummaryResponseDTO summary = new UserSummaryResponseDTO(1284L, 1102L, 12L, 45L);

        when(userService.getUserSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/users/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(1284))
                .andExpect(jsonPath("$.data.activeUsers").value(1102))
                .andExpect(jsonPath("$.data.adminUsers").value(12))
                .andExpect(jsonPath("$.data.pendingUsers").value(45));
    }

    @Test
    void updateCurrentUserProfileReturnsApiResponse() throws Exception {
        User user = User.builder().id(userId).email("user@example.com").role(Role.VISITOR).status(UserStatus.ACTIVE).build();
        UpdateProfileRequest request = new UpdateProfileRequest("User Name", "0912345678", "avatar.png");

        when(userService.updateCurrentUserProfile(any(User.class), any(UpdateProfileRequest.class))).thenReturn(response);
        authenticate(user);

        mockMvc.perform(patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("User Name"));
    }

    @Test
    void changeCurrentUserPasswordReturnsApiResponse() throws Exception {
        User user = User.builder().id(userId).email("user@example.com").role(Role.VISITOR).status(UserStatus.ACTIVE).build();
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");
        authenticate(user);

        mockMvc.perform(patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void authenticate(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
