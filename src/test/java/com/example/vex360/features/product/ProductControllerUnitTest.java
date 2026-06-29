package com.example.vex360.features.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.product.controllers.ProductController;
import com.example.vex360.features.product.dtos.request.CreateProductContentRequest;
import com.example.vex360.features.product.dtos.request.CreateProductRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;
import com.example.vex360.features.product.dtos.response.ProductContentResponseDTO;
import com.example.vex360.features.product.dtos.response.ProductResponseDTO;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.User;
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
    void createProductReturnsCreatedAndPassesMultipartFilesToService() throws Exception {
        authenticateExhibitor();
        CreateProductRequest request = new CreateProductRequest(
                "Robot",
                "VEX-001",
                UUID.randomUUID(),
                "Robot demo",
                BigDecimal.TEN,
                "VND",
                true,
                List.of(new CreateProductContentRequest("media_1", 0)));
        MockMultipartFile metadata = jsonPart("metadata", request);
        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail", "thumb.png", "image/png", "image".getBytes());
        MockMultipartFile media = new MockMultipartFile(
                "media_1", "front.png", "image/png", "image".getBytes());

        when(productService.createProduct(any(User.class), any(CreateProductRequest.class), any(MultipartFile.class), anyMap()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/products")
                .file(metadata)
                .file(thumbnail)
                .file(media))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sku").value("VEX-001"))
                .andExpect(jsonPath("$.data.contents[0].contentUrl").value("/front.png"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, MultipartFile>> filesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productService).createProduct(any(User.class), any(CreateProductRequest.class), any(MultipartFile.class), filesCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, filesCaptor.getValue().size());
        org.junit.jupiter.api.Assertions.assertTrue(filesCaptor.getValue().containsKey("media_1"));
    }

    @Test
    void updateProductReturnsOkAndUsesPatchMultipart() throws Exception {
        authenticateExhibitor();
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(
                "Robot",
                "VEX-001",
                UUID.randomUUID(),
                "Robot demo",
                BigDecimal.TEN,
                "VND",
                true,
                ProductStatus.ACTIVE,
                List.of(UUID.randomUUID()),
                List.of(new CreateProductContentRequest("media_2", 1)));
        MockMultipartFile metadata = jsonPart("metadata", request);
        MockMultipartFile media = new MockMultipartFile(
                "media_2", "side.png", "image/png", "image".getBytes());

        when(productService.updateProduct(any(User.class), any(UUID.class), any(UpdateProductRequest.class), any(), anyMap()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/products/{id}", productId)
                .file(metadata)
                .file(media)
                .with(req -> {
                    req.setMethod("PATCH");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.getId().toString()));
    }

    @Test
    void getProductsReturnsPageResponse() throws Exception {
        authenticateExhibitor();
        PageResponse<ProductResponseDTO> pageResponse = PageResponse.<ProductResponseDTO>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
        when(productService.getProducts(any(User.class), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Robot"));
    }

    private MockMultipartFile jsonPart(String name, Object value) throws Exception {
        return new MockMultipartFile(
                name,
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(value));
    }

    private ProductResponseDTO productResponse() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        return new ProductResponseDTO(
                productId,
                UUID.randomUUID(),
                categoryId,
                "Máy tính",
                "Robot",
                "VEX-001",
                "Robot demo",
                BigDecimal.TEN,
                "VND",
                "/thumb.png",
                ProductStatus.ACTIVE,
                true,
                List.of(new ProductContentResponseDTO(
                        UUID.randomUUID(),
                        "/front.png",
                        ProductContentType.IMAGE,
                        0,
                        "image/png",
                        200L)));
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
