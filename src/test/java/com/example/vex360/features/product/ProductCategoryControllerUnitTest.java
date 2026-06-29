package com.example.vex360.features.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.product.controllers.ProductCategoryController;
import com.example.vex360.features.product.dtos.request.CreateProductCategoryRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductCategoryStatusRequest;
import com.example.vex360.features.product.dtos.response.ProductCategoryResponseDTO;
import com.example.vex360.features.product.enums.ProductCategoryStatus;
import com.example.vex360.features.product.services.ProductCategoryService;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProductCategoryControllerUnitTest {
    @Mock
    private ProductCategoryService productCategoryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ProductCategoryResponseDTO response;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductCategoryController(productCategoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        response = new ProductCategoryResponseDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Máy tính",
                "Laptop và desktop",
                ProductCategoryStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getActiveCategoriesReturnsApiResponse() throws Exception {
        authenticateExhibitor();
        when(productCategoryService.getCategories(any(User.class), any(ProductCategoryStatus.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/product-categories?status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Máy tính"));
    }

    @Test
    void createCategoryReturnsCreatedApiResponse() throws Exception {
        authenticateExhibitor();
        CreateProductCategoryRequest request = new CreateProductCategoryRequest("Máy tính", "Laptop và desktop");
        when(productCategoryService.createCategory(any(User.class), any(CreateProductCategoryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/product-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void createCategoryRejectsBlankNameBeforeCallingService() throws Exception {
        authenticateExhibitor();
        CreateProductCategoryRequest request = new CreateProductCategoryRequest(" ", "Laptop và desktop");

        mockMvc.perform(post("/api/v1/product-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYS-003"));

        verifyNoInteractions(productCategoryService);
    }

    @Test
    void updateCategoryStatusReturnsOkApiResponse() throws Exception {
        authenticateExhibitor();
        ProductCategoryResponseDTO inactiveResponse = new ProductCategoryResponseDTO(
                response.getId(),
                response.getCompanyId(),
                response.getName(),
                response.getDescription(),
                ProductCategoryStatus.INACTIVE);
        when(productCategoryService.updateCategoryStatus(any(User.class), any(UUID.class), any(UpdateProductCategoryStatusRequest.class)))
                .thenReturn(inactiveResponse);

        mockMvc.perform(patch("/api/v1/product-categories/{id}/status", response.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new UpdateProductCategoryStatusRequest(ProductCategoryStatus.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    private void authenticateExhibitor() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .role(Role.EXHIBITOR)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
