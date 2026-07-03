package com.example.vex360.features.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.example.vex360.features.product.controllers.ProductController;
import com.example.vex360.features.product.dtos.request.CreateProductContentPreUploadedRequest;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductContentResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProductControllerUnitTest {

        @Mock
        private ProductService productService;

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;
        private ProductResponseDTO response;

        @BeforeEach
        void setup() {
                objectMapper = new ObjectMapper();
                objectMapper.findAndRegisterModules();
                mockMvc = MockMvcBuilders
                                .standaloneSetup(new ProductController(productService))
                                .setCustomArgumentResolvers(
                                                new PageableHandlerMethodArgumentResolver(),
                                                new AuthenticationPrincipalArgumentResolver())
                                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                                .build();
                response = productResponse();
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void createProductReturnsCreatedWithJsonBody() throws Exception {
                authenticateExhibitor();
                CreateProductRequest request = new CreateProductRequest(
                                "Robot", "VEX-001", UUID.randomUUID(), "Robot demo",
                                BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                                "http://cdn/thumb.png", "thumb-public-id",
                                List.of(new CreateProductContentPreUploadedRequest(
                                                "http://cdn/img.png", "pub-1", "image/png", 1000L, 0)));

                when(productService.createProduct(any(User.class), any(CreateProductRequest.class)))
                                .thenReturn(response);

                mockMvc.perform(post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.sku").value("VEX-001"));

                verify(productService).createProduct(any(User.class), any(CreateProductRequest.class));
        }

        @Test
        void updateProductReturnsOkWithJsonBody() throws Exception {
                authenticateExhibitor();
                UUID productId = UUID.randomUUID();
                UpdateProductRequest request = new UpdateProductRequest(
                                "Robot", "VEX-001", UUID.randomUUID(), "Robot demo",
                                BigDecimal.TEN, "VND", ProductStatus.ACTIVE,
                                null, null,
                                List.of(UUID.randomUUID()),
                                List.of(new CreateProductContentPreUploadedRequest(
                                                "http://cdn/img.png", "pub-1", "image/png", 1000L, 0)));

                when(productService.updateProduct(any(User.class), any(UUID.class), any(UpdateProductRequest.class)))
                                .thenReturn(response);

                mockMvc.perform(patch("/api/v1/products/{id}", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(response.getId().toString()));

                verify(productService).updateProduct(any(User.class), eq(productId), any(UpdateProductRequest.class));
        }

        @Test
        void getProductsReturnsPageResponse() throws Exception {
                authenticateExhibitor();
                PageResponse<ProductResponseDTO> pageResponse = PageResponse.<ProductResponseDTO>builder()
                                .content(List.of(response)).page(0).size(10)
                                .totalElements(1).totalPages(1).first(true).last(true).build();

                when(productService.getProducts(
                                any(User.class), eq("Robot"), any(UUID.class),
                                eq(ProductStatus.ACTIVE), any(Pageable.class)))
                                .thenReturn(pageResponse);

                UUID categoryId = UUID.randomUUID();
                mockMvc.perform(get("/api/v1/products")
                                .param("keyword", "Robot")
                                .param("categoryId", categoryId.toString())
                                .param("status", "ACTIVE"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content[0].name").value("Robot"));
        }

        // ─── Helpers ─────────────────────────────────────────────────────────────

        private ProductResponseDTO productResponse() {
                return new ProductResponseDTO(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                "Máy tính", "Robot", "VEX-001", "Robot demo",
                                BigDecimal.TEN, "VND", "/thumb.png", ProductStatus.ACTIVE,
                                List.of(new ProductContentResponseDTO(
                                                UUID.randomUUID(), "/front.png", ProductContentType.IMAGE, 0,
                                                "image/png", 200L)));
        }

        private void authenticateExhibitor() {
                User user = User.builder()
                                .id(UUID.randomUUID()).email("owner@example.com")
                                .role(Role.EXHIBITOR).status(UserStatus.ACTIVE).build();
                CustomUserDetails userDetails = new CustomUserDetails(user);
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(userDetails, null,
                                                userDetails.getAuthorities()));
        }
}
