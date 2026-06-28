package com.example.vex360.features.exhibition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.exhibition.controllers.AdminExhibitionController;
import com.example.vex360.features.exhibition.dtos.request.RejectExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionSummaryResponseDTO;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminExhibitionControllerTest {

    @Mock
    private ExhibitionService exhibitionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID exhibitionUuid;
    private ExhibitionResponseDTO exhibitionResponse;
    private User adminUser;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminExhibitionController(exhibitionService))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        exhibitionUuid = UUID.randomUUID();
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .build();

        exhibitionResponse = ExhibitionResponseDTO.builder()
                .id(1)
                .uuid(exhibitionUuid)
                .name("Virtual Expo 2026")
                .category("Technology")
                .description("A high tech virtual expo")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 10))
                .estimatedBooths(50)
                .status("PENDING")
                .organizerName("John Doe")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getExhibitionsReturnsPagedApiResponse() throws Exception {
        PageResponse<ExhibitionResponseDTO> pageResponse = PageResponse.<ExhibitionResponseDTO>builder()
                .content(List.of(exhibitionResponse))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(exhibitionService.searchExhibitionsForAdmin(
                eq("Virtual"),
                eq(ExhibitionStatus.PENDING),
                eq("Technology"),
                eq(LocalDate.of(2026, 7, 1)),
                eq(LocalDate.of(2026, 7, 10)),
                any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/exhibitions")
                .param("keyword", "Virtual")
                .param("status", "PENDING")
                .param("category", "Technology")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].uuid").value(exhibitionUuid.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("Virtual Expo 2026"))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    void getExhibitionSummaryReturnsApiResponse() throws Exception {
        ExhibitionSummaryResponseDTO summary = ExhibitionSummaryResponseDTO.builder()
                .totalExhibitions(10L)
                .pendingExhibitions(3L)
                .approvedExhibitions(5L)
                .rejectedExhibitions(2L)
                .activeExhibitions(0L)
                .statusCounts(Map.of("PENDING", 3L, "APPROVED", 5L, "REJECTED", 2L))
                .build();

        when(exhibitionService.getExhibitionSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/exhibitions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExhibitions").value(10))
                .andExpect(jsonPath("$.data.pendingExhibitions").value(3))
                .andExpect(jsonPath("$.data.approvedExhibitions").value(5))
                .andExpect(jsonPath("$.data.statusCounts.PENDING").value(3));
    }

    @Test
    void getExhibitionByUuidReturnsDetailApiResponse() throws Exception {
        when(exhibitionService.getExhibitionDetailForAdmin(exhibitionUuid)).thenReturn(exhibitionResponse);

        mockMvc.perform(get("/api/v1/admin/exhibitions/{uuid}", exhibitionUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value(exhibitionUuid.toString()))
                .andExpect(jsonPath("$.data.name").value("Virtual Expo 2026"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void approveExhibitionSuccess() throws Exception {
        authenticate(adminUser);
        ExhibitionResponseDTO approvedResponse = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .status("APPROVED")
                .reviewedByName("Admin User")
                .build();

        when(exhibitionService.approveExhibition(any(User.class), eq(exhibitionUuid))).thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/admin/exhibitions/{uuid}/approve", exhibitionUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void rejectExhibitionSuccess() throws Exception {
        authenticate(adminUser);
        RejectExhibitionRequest request = new RejectExhibitionRequest("Exhibition duration is too short");
        ExhibitionResponseDTO rejectedResponse = ExhibitionResponseDTO.builder()
                .uuid(exhibitionUuid)
                .status("REJECTED")
                .rejectedReason("Exhibition duration is too short")
                .build();

        when(exhibitionService.rejectExhibition(any(User.class), eq(exhibitionUuid), any(RejectExhibitionRequest.class)))
                .thenReturn(rejectedResponse);

        mockMvc.perform(post("/api/v1/admin/exhibitions/{uuid}/reject", exhibitionUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectedReason").value("Exhibition duration is too short"));
    }

    @Test
    void rejectExhibitionFailsWhenReasonIsBlank() throws Exception {
        authenticate(adminUser);
        RejectExhibitionRequest request = new RejectExhibitionRequest(" ");

        mockMvc.perform(post("/api/v1/admin/exhibitions/{uuid}/reject", exhibitionUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    private void authenticate(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
