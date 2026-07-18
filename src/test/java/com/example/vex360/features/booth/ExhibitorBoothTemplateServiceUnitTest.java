package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.ExhibitorBoothTemplateSummaryResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothContentCountProjection;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothTemplateServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;
    @Mock
    private PanoramaRepository panoramaRepository;
    @Mock
    private HotspotRepository hotspotRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    private ExhibitorBoothTemplateService service;
    private User exhibitor;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setup() {
        service = new ExhibitorBoothTemplateService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                companyService,
                boothReviewPolicyService,
                Mappers.getMapper(BoothMapper.class));
        exhibitor = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitor).name("Company").build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .maxPanoramasPerBoothSnapshot(2)
                .maxHotspotsPerBoothSnapshot(3)
                .build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .createdBy(exhibitor)
                .exhibitorRegistration(registration)
                .build();
    }

    @Test
    void getCompatibleTemplatesUsesPackageLimitsAndBatchCounts() {
        Booth template = templateBooth();
        PageRequest pageable = PageRequest.of(0, 10);
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(boothRepository.searchCompatibleTemplates("modern", BoothStatus.PUBLISHED, 2, 3, pageable))
                .thenReturn(new PageImpl<>(List.of(template), pageable, 1));
        when(panoramaRepository.countByBoothIds(List.of(template.getId())))
                .thenReturn(List.of(count(template.getId(), 2)));
        when(hotspotRepository.countByBoothIds(List.of(template.getId())))
                .thenReturn(List.of(count(template.getId(), 1)));

        PageResponse<ExhibitorBoothTemplateSummaryResponseDTO> response = service.getCompatibleTemplates(
                exhibitor, booth.getId(), " modern ", pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(2L, response.getContent().get(0).getPanoramaCount());
        assertEquals(1L, response.getContent().get(0).getHotspotCount());
    }

    @Test
    void applyTemplateCopiesPanoramasAndNavigationHotspots() {
        Booth template = templateBooth();
        List<Panorama> templatePanoramas = templatePanoramas(template);
        List<Panorama> managedCollection = booth.getPanoramas();
        UUID templateSourceId = templatePanoramas.get(0).getId();
        UUID templateTargetId = templatePanoramas.get(1).getId();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(0L);
        when(boothRepository.findTemplateByIdForUpdate(template.getId())).thenReturn(Optional.of(template));
        when(panoramaRepository.findDetailsByBoothId(template.getId())).thenReturn(templatePanoramas);
        when(panoramaRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Panorama> copies = invocation.getArgument(0);
            copies.forEach(panorama -> panorama.setId(UUID.randomUUID()));
            return copies;
        });
        when(hotspotRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        BoothResponseDTO response = service.applyTemplate(exhibitor, booth.getId(), template.getId());

        assertSame(managedCollection, booth.getPanoramas());
        assertEquals("Runtime Booth", response.getName());
        assertEquals(2, response.getPanoramas().size());
        assertTrue(response.getPanoramas().stream().allMatch(p -> {
            Panorama saved = booth.getPanoramas().stream()
                    .filter(candidate -> candidate.getId().equals(p.getId()))
                    .findFirst().orElseThrow();
            return Boolean.TRUE.equals(saved.getIsTemplateDerived());
        }));
        assertNotEquals(templateSourceId, response.getPanoramas().get(0).getId());
        assertNotEquals(templateTargetId, response.getPanoramas().get(1).getId());
        assertEquals("shared/entrance", response.getPanoramas().get(0).getImageKey());
        assertEquals(response.getPanoramas().get(1).getId(),
                response.getPanoramas().get(0).getHotspots().get(0).getTargetPanoramaId());
    }

    @Test
    void applyTemplateRejectsNonEmptyBoothBeforeLoadingTemplate() {
        Booth template = templateBooth();
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(1L);

        AppException exception = assertThrows(AppException.class,
                () -> service.applyTemplate(exhibitor, booth.getId(), template.getId()));

        assertSame(ErrorCode.BOOTH_TEMPLATE_REQUIRES_EMPTY_BOOTH, exception.getErrorCode());
        verify(boothRepository, never()).findTemplateByIdForUpdate(template.getId());
    }

    @Test
    void applyTemplateRejectsTemplateOverPackagePanoramaLimit() {
        Booth template = templateBooth();
        List<Panorama> templatePanoramas = templatePanoramas(template);
        templatePanoramas.add(Panorama.builder()
                .id(UUID.randomUUID())
                .booth(template)
                .name("Extra")
                .imageUrl("https://cdn/extra.jpg")
                .imageKey("shared/extra")
                .orderIndex(2)
                .isDefault(false)
                .build());
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.countByBoothId(booth.getId())).thenReturn(0L);
        when(boothRepository.findTemplateByIdForUpdate(template.getId())).thenReturn(Optional.of(template));
        when(panoramaRepository.findDetailsByBoothId(template.getId())).thenReturn(templatePanoramas);

        AppException exception = assertThrows(AppException.class,
                () -> service.applyTemplate(exhibitor, booth.getId(), template.getId()));

        assertSame(ErrorCode.BOOTH_TEMPLATE_NOT_COMPATIBLE, exception.getErrorCode());
        verify(panoramaRepository, never()).saveAll(anyList());
    }

    private Booth templateBooth() {
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Modern Template")
                .status(BoothStatus.PUBLISHED)
                .isTemplate(true)
                .createdBy(exhibitor)
                .build();
    }

    private List<Panorama> templatePanoramas(Booth template) {
        Panorama entrance = Panorama.builder()
                .id(UUID.randomUUID())
                .booth(template)
                .name("Entrance")
                .imageUrl("https://cdn/entrance.jpg")
                .imageKey("shared/entrance")
                .orderIndex(0)
                .isDefault(true)
                .build();
        Panorama main = Panorama.builder()
                .id(UUID.randomUUID())
                .booth(template)
                .name("Main")
                .imageUrl("https://cdn/main.jpg")
                .imageKey("shared/main")
                .orderIndex(1)
                .isDefault(false)
                .build();
        entrance.getHotspots().add(Hotspot.builder()
                .type(HotspotType.NAV)
                .name("Go main")
                .sourcePanorama(entrance)
                .targetPanorama(main)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build());
        return new java.util.ArrayList<>(List.of(entrance, main));
    }

    private BoothContentCountProjection count(UUID boothId, long contentCount) {
        return new BoothContentCountProjection() {
            @Override
            public UUID getBoothId() {
                return boothId;
            }

            @Override
            public Long getContentCount() {
                return contentCount;
            }
        };
    }
}
