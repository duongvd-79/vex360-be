package com.example.vex360.features.packagetemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.packagetemplate.controllers.AdminPackageTemplateController;
import com.example.vex360.features.packagetemplate.controllers.PackageTemplateController;
import com.example.vex360.features.packagetemplate.dtos.request.CreatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateStatusRequest;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PackageTemplateControllerUnitTest {
    @Mock
    private PackageTemplateService packageTemplateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PackageTemplateResponseDTO response;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new PackageTemplateController(packageTemplateService),
                        new AdminPackageTemplateController(packageTemplateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        response = new PackageTemplateResponseDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin@example.com",
                "Pro",
                "Pro package",
                BigDecimal.valueOf(99000),
                "VND",
                15,
                3,
                3,
                30,
                2048L,
                BoothListingPriority.PRIORITY,
                PackageTemplateStatus.ACTIVE,
                null,
                null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPackageTemplateReturnsCreatedApiResponse() throws Exception {
        authenticateAdmin();
        CreatePackageTemplateRequest request = new CreatePackageTemplateRequest(
                "Pro",
                "Pro package",
                BigDecimal.valueOf(99000),
                "VND",
                15,
                3,
                3,
                30,
                2048L,
                BoothListingPriority.PRIORITY);
        when(packageTemplateService.createPackageTemplate(any(User.class), any(CreatePackageTemplateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/package-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Pro"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getPackageTemplatesReturnsPagedApiResponse() throws Exception {
        PageResponse<PackageTemplateResponseDTO> pageResponse = PageResponse.<PackageTemplateResponseDTO>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
        when(packageTemplateService.getPackageTemplates(eq("pro"), eq(PackageTemplateStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/package-templates?keyword=pro&status=ACTIVE&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Pro"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getActivePackageTemplatesReturnsApiResponse() throws Exception {
        when(packageTemplateService.getActivePackageTemplates()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/package-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingPriority").value("PRIORITY"));
    }

    @Test
    void createPackageTemplateRejectsBlankNameBeforeCallingService() throws Exception {
        authenticateAdmin();
        CreatePackageTemplateRequest request = new CreatePackageTemplateRequest(
                " ",
                "Pro package",
                BigDecimal.valueOf(99000),
                "VND",
                15,
                3,
                3,
                30,
                2048L,
                BoothListingPriority.PRIORITY);

        mockMvc.perform(post("/api/v1/admin/package-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[0].message")
                        .value("Tên gói không được để trống"));

        verifyNoInteractions(packageTemplateService);
    }

    @Test
    void createPackageTemplateRejectsInvalidQuotaBeforeCallingService() throws Exception {
        authenticateAdmin();
        CreatePackageTemplateRequest request = new CreatePackageTemplateRequest(
                "Pro",
                "Pro package",
                BigDecimal.valueOf(99000),
                "VND",
                0,
                3,
                3,
                30,
                2048L,
                BoothListingPriority.PRIORITY);

        mockMvc.perform(post("/api/v1/admin/package-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"))
                .andExpect(jsonPath("$.validationErrors[0].message")
                        .value("Số sản phẩm tối đa mỗi gian hàng phải lớn hơn hoặc bằng 1"));

        verifyNoInteractions(packageTemplateService);
    }

    @Test
    void updatePackageTemplateStatusReturnsOkApiResponse() throws Exception {
        PackageTemplateResponseDTO inactiveResponse = new PackageTemplateResponseDTO(
                response.getId(),
                response.getCreatedById(),
                response.getCreatedByEmail(),
                response.getName(),
                response.getDescription(),
                response.getPrice(),
                response.getCurrency(),
                response.getMaxProductsPerBooth(),
                response.getMaxEmbeddedVideosPerBooth(),
                response.getMaxPanoramasPerBooth(),
                response.getMaxHotspotsPerBooth(),
                response.getStorageLimitMb(),
                response.getListingPriority(),
                PackageTemplateStatus.INACTIVE,
                response.getCreatedAt(),
                response.getUpdatedAt());
        when(packageTemplateService.updatePackageTemplateStatus(any(UUID.class), any(UpdatePackageTemplateStatusRequest.class)))
                .thenReturn(inactiveResponse);

        mockMvc.perform(patch("/api/v1/admin/package-templates/{id}/status", response.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new UpdatePackageTemplateStatusRequest(PackageTemplateStatus.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void deletePackageTemplateEndpointIsNotExposed() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/package-templates/{id}", response.getId()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(packageTemplateService);
    }

    private void authenticateAdmin() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
