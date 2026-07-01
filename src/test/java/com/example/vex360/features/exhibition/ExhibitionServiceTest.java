package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.exhibition.dtos.request.ConfigureExhibitionPackageRequest;
import com.example.vex360.features.exhibition.dtos.request.CreateExhibitionRequest;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionPackageResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionResponseDTO;
import com.example.vex360.features.exhibition.mapper.ExhibitionMapper;
import com.example.vex360.features.exhibition.repositories.ExhibitionPackageRepository;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.exhibition.services.impl.ExhibitionServiceImpl;
import com.example.vex360.features.packagetemplate.repositories.PackageTemplateRepository;
import com.example.vex360.shared.entities.Exhibition;
import com.example.vex360.shared.entities.ExhibitionPackage;
import com.example.vex360.shared.entities.PackageTemplate;
import com.example.vex360.shared.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.features.exhibition.repositories.ExhibitionAssetRepository;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import org.springframework.web.multipart.MultipartFile;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ExhibitionServiceTest {

    @Mock
    private ExhibitionRepository exhibitionRepository;

    @Mock
    private ExhibitionPackageRepository exhibitionPackageRepository;

    @Mock
    private PackageTemplateRepository packageTemplateRepository;

    @Mock
    private ExhibitionMapper exhibitionMapper;

    @Mock
    private ExhibitionAssetRepository exhibitionAssetRepository;

    @Mock
    private CloudService cloudService;

    @InjectMocks
    private ExhibitionServiceImpl exhibitionService;

    private User organizerUser;
    private User otherUser;
    private Exhibition exhibition;
    private PackageTemplate packageTemplate;
    private ExhibitionPackage exhibitionPackage;

    @BeforeEach
    public void setup() {
        organizerUser = User.builder()
                .id(UUID.randomUUID())
                .email("organizer@example.com")
                .fullName("Organizer Jane")
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .fullName("Other John")
                .build();

        exhibition = Exhibition.builder()
                .id(1)
                .organizer(organizerUser)
                .uuid(UUID.randomUUID())
                .name("Virtual Expo 2026")
                .status(ExhibitionStatus.ACTIVE)
                .build();

        packageTemplate = PackageTemplate.builder()
                .id(UUID.randomUUID())
                .name("Platinum Package")
                .price(BigDecimal.valueOf(5000000))
                .build();

        exhibitionPackage = ExhibitionPackage.builder()
                .id(20)
                .exhibition(exhibition)
                .template(packageTemplate)
                .finalPrice(BigDecimal.valueOf(6000000))
                .status(ExhibitionPackageStatus.ACTIVE)
                .build();
    }

    @Test
    public void testCreateExhibition_Success() {
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Virtual Expo 2026")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(50)
                .build();

        MultipartFile keyVisual = mock(MultipartFile.class);
        when(keyVisual.isEmpty()).thenReturn(false);
        when(keyVisual.getContentType()).thenReturn("image/png");
        when(keyVisual.getSize()).thenReturn(1000L);

        when(exhibitionRepository.countByOrganizerIdAndStatus(organizerUser.getId(), ExhibitionStatus.PENDING)).thenReturn(0L);
        when(exhibitionRepository.existsByName("Virtual Expo 2026")).thenReturn(false);
        when(exhibitionRepository.save(any(Exhibition.class))).thenReturn(exhibition);
        when(cloudService.upload(any(MultipartFile.class))).thenReturn(CloudinaryResponse.builder()
                .url("http://example.com/logo.png")
                .publicId("logo_pid")
                .build());
        when(exhibitionMapper.toResponse(any(Exhibition.class), anyList()))
                .thenReturn(ExhibitionResponseDTO.builder().name("Virtual Expo 2026").build());

        ExhibitionResponseDTO response = exhibitionService.createExhibition(organizerUser, request, keyVisual);

        assertNotNull(response);
        assertEquals("Virtual Expo 2026", response.getName());
        verify(exhibitionRepository).save(any(Exhibition.class));
        verify(exhibitionAssetRepository).save(any());
    }

    @Test
    public void testCreateExhibition_LimitExceeded() {
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Another Expo")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(50)
                .build();

        MultipartFile keyVisual = mock(MultipartFile.class);
        when(keyVisual.isEmpty()).thenReturn(false);
        when(keyVisual.getContentType()).thenReturn("image/png");
        when(keyVisual.getSize()).thenReturn(1000L);

        when(exhibitionRepository.countByOrganizerIdAndStatus(organizerUser.getId(), ExhibitionStatus.PENDING)).thenReturn(3L);

        AppException exception = assertThrows(AppException.class, () -> {
            exhibitionService.createExhibition(organizerUser, request, keyVisual);
        });

        assertEquals(ErrorCode.EXHIBITION_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(exhibitionRepository, never()).save(any());
    }

    @Test
    public void testCreateExhibition_DuplicateName() {
        CreateExhibitionRequest request = CreateExhibitionRequest.builder()
                .name("Virtual Expo 2026")
                .category("Technology")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .estimatedBooths(50)
                .build();

        MultipartFile keyVisual = mock(MultipartFile.class);
        when(keyVisual.isEmpty()).thenReturn(false);
        when(keyVisual.getContentType()).thenReturn("image/png");
        when(keyVisual.getSize()).thenReturn(1000L);

        when(exhibitionRepository.countByOrganizerIdAndStatus(organizerUser.getId(), ExhibitionStatus.PENDING)).thenReturn(0L);
        when(exhibitionRepository.existsByName("Virtual Expo 2026")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> {
            exhibitionService.createExhibition(organizerUser, request, keyVisual);
        });

        assertEquals(ErrorCode.EXHIBITION_NAME_DUPLICATED, exception.getErrorCode());
        verify(exhibitionRepository, never()).save(any());
    }

    @Test
    public void testConfigureExhibitionPackage_Success() {
        ConfigureExhibitionPackageRequest request = ConfigureExhibitionPackageRequest.builder()
                .templateId(packageTemplate.getId())
                .finalPrice(BigDecimal.valueOf(6000000))
                .build();

        when(exhibitionRepository.findByUuid(exhibition.getUuid())).thenReturn(Optional.of(exhibition));
        when(packageTemplateRepository.findById(packageTemplate.getId())).thenReturn(Optional.of(packageTemplate));
        when(exhibitionPackageRepository.findByExhibitionIdAndTemplateId(exhibition.getId(), packageTemplate.getId()))
                .thenReturn(Optional.empty());
        when(exhibitionPackageRepository.save(any(ExhibitionPackage.class))).thenReturn(exhibitionPackage);
        when(exhibitionMapper.toPackageResponse(any(ExhibitionPackage.class)))
                .thenReturn(ExhibitionPackageResponseDTO.builder().finalPrice(BigDecimal.valueOf(6000000)).build());

        ExhibitionPackageResponseDTO response = exhibitionService.configureExhibitionPackage(organizerUser, exhibition.getUuid(), request);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(6000000), response.getFinalPrice());
        verify(exhibitionPackageRepository).save(any(ExhibitionPackage.class));
    }

    @Test
    public void testConfigureExhibitionPackage_ValidationFailed_PriceTooLow() {
        ConfigureExhibitionPackageRequest request = ConfigureExhibitionPackageRequest.builder()
                .templateId(packageTemplate.getId())
                .finalPrice(BigDecimal.valueOf(4000000)) // < floorPrice (5M)
                .build();

        when(exhibitionRepository.findByUuid(exhibition.getUuid())).thenReturn(Optional.of(exhibition));
        when(packageTemplateRepository.findById(packageTemplate.getId())).thenReturn(Optional.of(packageTemplate));

        AppException exception = assertThrows(AppException.class, () -> {
            exhibitionService.configureExhibitionPackage(organizerUser, exhibition.getUuid(), request);
        });

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(exhibitionPackageRepository, never()).save(any());
    }

    @Test
    public void testConfigureExhibitionPackage_Unauthorized() {
        ConfigureExhibitionPackageRequest request = ConfigureExhibitionPackageRequest.builder()
                .templateId(packageTemplate.getId())
                .finalPrice(BigDecimal.valueOf(6000000))
                .build();

        when(exhibitionRepository.findByUuid(exhibition.getUuid())).thenReturn(Optional.of(exhibition));

        AppException exception = assertThrows(AppException.class, () -> {
            exhibitionService.configureExhibitionPackage(otherUser, exhibition.getUuid(), request);
        });

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(exhibitionPackageRepository, never()).save(any());
    }

    @Test
    public void testGetExhibitionByUuid_Success() {
        UUID uuid = exhibition.getUuid();
        when(exhibitionRepository.findByUuid(uuid)).thenReturn(Optional.of(exhibition));
        when(exhibitionPackageRepository.findByExhibition(exhibition)).thenReturn(List.of(exhibitionPackage));
        
        ExhibitionResponseDTO expectedResponse = ExhibitionResponseDTO.builder()
                .uuid(uuid)
                .name("Virtual Expo 2026")
                .build();
        when(exhibitionMapper.toPublicResponse(eq(exhibition), anyList())).thenReturn(expectedResponse);

        ExhibitionResponseDTO response = exhibitionService.getExhibitionByUuid(uuid);

        assertNotNull(response);
        assertEquals(uuid, response.getUuid());
        assertNull(response.getId()); // verify ID is hidden
    }
}
