package com.example.vex360.features.packagetemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.vex360.features.packagetemplate.dtos.request.CreatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateRequest;
import com.example.vex360.features.packagetemplate.dtos.request.UpdatePackageTemplateStatusRequest;
import com.example.vex360.features.packagetemplate.dtos.response.PackageTemplateResponseDTO;
import com.example.vex360.features.packagetemplate.mapper.PackageTemplateMapper;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.features.packagetemplate.services.PackageTemplateService;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.PackageTemplateStatus;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PackageTemplateServiceUnitTest {
    @Mock
    private PackageTemplateRepository packageTemplateRepository;

    private PackageTemplateService packageTemplateService;
    private User admin;

    @BeforeEach
    void setup() {
        packageTemplateService = new PackageTemplateService(
                packageTemplateRepository,
                new PackageTemplateMapper());
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createPackageTemplateStoresTrimmedActivePackageWithDefaultCurrency() {
        CreatePackageTemplateRequest request = new CreatePackageTemplateRequest(
                " Pro ",
                "Pro package",
                BigDecimal.valueOf(99000),
                " ",
                15,
                3,
                3,
                30,
                2048L,
                BoothListingPriority.PRIORITY);

        when(packageTemplateRepository.existsByNameIgnoreCase("Pro")).thenReturn(false);
        when(packageTemplateRepository.save(any(PackageTemplate.class))).thenAnswer(invocation -> {
            PackageTemplate template = invocation.getArgument(0);
            template.setId(UUID.randomUUID());
            return template;
        });

        PackageTemplateResponseDTO response = packageTemplateService.createPackageTemplate(admin, request);

        ArgumentCaptor<PackageTemplate> captor = ArgumentCaptor.forClass(PackageTemplate.class);
        verify(packageTemplateRepository).save(captor.capture());
        PackageTemplate saved = captor.getValue();
        assertEquals(admin, saved.getCreatedBy());
        assertEquals("Pro", saved.getName());
        assertEquals("VND", saved.getCurrency());
        assertEquals(PackageTemplateStatus.ACTIVE, saved.getStatus());
        assertEquals(15, saved.getMaxProductsPerBooth());
        assertEquals(3, saved.getMaxEmbeddedVideosPerBooth());
        assertEquals(3, saved.getMaxPanoramasPerBooth());
        assertEquals(30, saved.getMaxHotspotsPerBooth());
        assertEquals(2048L, saved.getStorageLimitMb());
        assertEquals(BoothListingPriority.PRIORITY, saved.getListingPriority());
        assertEquals("Pro", response.getName());
    }

    @Test
    void createPackageTemplateRejectsDuplicateName() {
        CreatePackageTemplateRequest request = sampleCreateRequest("Free");
        when(packageTemplateRepository.existsByNameIgnoreCase("Free")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> packageTemplateService.createPackageTemplate(admin, request));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NAME_DUPLICATED, exception.getErrorCode());
        verify(packageTemplateRepository, never()).save(any(PackageTemplate.class));
    }

    @Test
    void getPackageTemplatesSearchesAdminPackagesWithFilters() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));
        PackageTemplate template = sampleTemplate("Pro", PackageTemplateStatus.ACTIVE);
        when(packageTemplateRepository.searchPackageTemplates("pro", PackageTemplateStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(template), pageable, 1));

        PageResponse<PackageTemplateResponseDTO> response = packageTemplateService
                .getPackageTemplates(" pro ", PackageTemplateStatus.ACTIVE, pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals("Pro", response.getContent().get(0).getName());
        verify(packageTemplateRepository).searchPackageTemplates("pro", PackageTemplateStatus.ACTIVE, pageable);
    }

    @Test
    void getActivePackageTemplatesOnlyReturnsActivePackages() {
        PackageTemplate template = sampleTemplate("Free", PackageTemplateStatus.ACTIVE);
        when(packageTemplateRepository.findByStatus(eq(PackageTemplateStatus.ACTIVE), any(Sort.class)))
                .thenReturn(List.of(template));

        List<PackageTemplateResponseDTO> response = packageTemplateService.getActivePackageTemplates();

        assertEquals(1, response.size());
        assertEquals(PackageTemplateStatus.ACTIVE, response.get(0).getStatus());
        verify(packageTemplateRepository).findByStatus(
                eq(PackageTemplateStatus.ACTIVE),
                eq(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("name"))));
    }

    @Test
    void getActivePackageTemplateByIdRejectsInactivePackage() {
        UUID packageId = UUID.randomUUID();
        when(packageTemplateRepository.findByIdAndStatus(packageId, PackageTemplateStatus.ACTIVE))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> packageTemplateService.getActivePackageTemplateById(packageId));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updatePackageTemplateRejectsDuplicateName() {
        UUID packageId = UUID.randomUUID();
        PackageTemplate template = sampleTemplate("Free", PackageTemplateStatus.ACTIVE);
        template.setId(packageId);

        when(packageTemplateRepository.findById(packageId)).thenReturn(Optional.of(template));
        when(packageTemplateRepository.existsByNameIgnoreCaseAndIdNot("Pro", packageId)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> packageTemplateService.updatePackageTemplate(packageId, sampleUpdateRequest("Pro")));

        assertEquals(ErrorCode.PACKAGE_TEMPLATE_NAME_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void updatePackageTemplateStatusSetsRequestedStatus() {
        UUID packageId = UUID.randomUUID();
        PackageTemplate template = sampleTemplate("Pro", PackageTemplateStatus.ACTIVE);
        template.setId(packageId);

        when(packageTemplateRepository.findById(packageId)).thenReturn(Optional.of(template));
        when(packageTemplateRepository.save(template)).thenReturn(template);

        PackageTemplateResponseDTO response = packageTemplateService.updatePackageTemplateStatus(
                packageId,
                new UpdatePackageTemplateStatusRequest(PackageTemplateStatus.INACTIVE));

        assertEquals(PackageTemplateStatus.INACTIVE, template.getStatus());
        assertEquals(PackageTemplateStatus.INACTIVE, response.getStatus());
        verify(packageTemplateRepository).save(template);
    }

    private CreatePackageTemplateRequest sampleCreateRequest(String name) {
        return new CreatePackageTemplateRequest(
                name,
                "Package description",
                BigDecimal.ZERO,
                "VND",
                7,
                1,
                1,
                10,
                500L,
                BoothListingPriority.NORMAL);
    }

    private UpdatePackageTemplateRequest sampleUpdateRequest(String name) {
        return new UpdatePackageTemplateRequest(
                name,
                "Updated package",
                BigDecimal.valueOf(1000),
                "usd",
                20,
                5,
                5,
                50,
                10240L,
                BoothListingPriority.FEATURED);
    }

    private PackageTemplate sampleTemplate(String name, PackageTemplateStatus status) {
        return PackageTemplate.builder()
                .id(UUID.randomUUID())
                .createdBy(admin)
                .name(name)
                .description("Package description")
                .price(BigDecimal.ZERO)
                .currency("VND")
                .maxProductsPerBooth(7)
                .maxEmbeddedVideosPerBooth(1)
                .maxPanoramasPerBooth(1)
                .maxHotspotsPerBooth(10)
                .storageLimitMb(500L)
                .listingPriority(BoothListingPriority.NORMAL)
                .status(status)
                .build();
    }
}
