package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.company.entities.Company;

class BoothMapperUnitTest {

    private final BoothMapper boothMapper = Mappers.getMapper(BoothMapper.class);

    @Test
    void toBoothResponseDTO_ShouldMapAllCompanyFields_WhenCompanyIsPresent() {
        // Given
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .name("Công Ty Công Nghệ 360")
                .industry("Nội thất & Kiến trúc")
                .email("contact@company360.vn")
                .phone("0987654321")
                .address("123 Đường ABC, Quận 1, TP.HCM")
                .website("https://company360.vn")
                .logoUrl("https://company360.vn/logo.png")
                .description("Công ty hàng đầu về thiết kế nội thất 360")
                .build();

        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Gian hàng 360")
                .description("Mô tả gian hàng 360")
                .company(company)
                .build();

        // When
        BoothResponseDTO response = boothMapper.toBoothResponseDTO(booth, List.of());

        // Then
        assertNotNull(response);
        assertEquals(company.getId(), response.getCompanyId());
        assertEquals("Công Ty Công Nghệ 360", response.getCompanyName());
        assertEquals("Nội thất & Kiến trúc", response.getCompanyIndustry());
        assertEquals("contact@company360.vn", response.getCompanyEmail());
        assertEquals("0987654321", response.getCompanyPhone());
        assertEquals("123 Đường ABC, Quận 1, TP.HCM", response.getCompanyAddress());
        assertEquals("https://company360.vn", response.getCompanyWebsite());
        assertEquals("https://company360.vn/logo.png", response.getCompanyLogoUrl());
        assertEquals("Công ty hàng đầu về thiết kế nội thất 360", response.getCompanyDescription());
    }

    @Test
    void toBoothResponseDTO_ShouldHandleNullCompany_WithoutThrowing() {
        // Given
        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Gian hàng 360")
                .company(null)
                .build();

        // When
        BoothResponseDTO response = boothMapper.toBoothResponseDTO(booth, List.of());

        // Then
        assertNotNull(response);
        assertNull(response.getCompanyId());
        assertNull(response.getCompanyName());
        assertNull(response.getCompanyIndustry());
        assertNull(response.getCompanyEmail());
        assertNull(response.getCompanyPhone());
        assertNull(response.getCompanyAddress());
        assertNull(response.getCompanyWebsite());
        assertNull(response.getCompanyLogoUrl());
        assertNull(response.getCompanyDescription());
    }
}
