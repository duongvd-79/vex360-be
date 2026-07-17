package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapperImpl;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorBoothTemplateService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.BoothListingPriority;
import com.example.vex360.shared.enums.ExhibitionPackageStatus;
import com.example.vex360.shared.enums.ExhibitionStatus;
import com.example.vex360.shared.enums.ExhibitorRegistrationStatus;
import com.example.vex360.shared.enums.Role;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=false"
})
@Import({ ExhibitorBoothTemplateService.class, BoothMapperImpl.class })
class ExhibitorBoothTemplateServiceJpaIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ExhibitorBoothTemplateService service;
    @Autowired
    private BoothRepository boothRepository;
    @Autowired
    private PanoramaRepository panoramaRepository;

    @MockitoBean
    private CompanyService companyService;
    @MockitoBean
    private BoothReviewPolicyService boothReviewPolicyService;

    @Test
    void applyTemplateKeepsManagedPanoramaCollectionThroughCommit() {
        Fixture fixture = persistFixture();
        entityManager.flush();
        entityManager.clear();
        when(companyService.getCompanyEntityForCurrentUser(fixture.exhibitor())).thenReturn(fixture.company());

        BoothResponseDTO response = service.applyTemplate(
                fixture.exhibitor(), fixture.boothId(), fixture.templateId());

        assertEquals(2, response.getPanoramas().size());
        assertDoesNotThrow(() -> {
            entityManager.flush();
            TestTransaction.flagForCommit();
            TestTransaction.end();
        });

        TestTransaction.start();
        Booth appliedBooth = boothRepository.findById(fixture.boothId()).orElseThrow();
        List<Panorama> appliedPanoramas = panoramaRepository.findDetailsByBoothId(appliedBooth.getId());
        List<Panorama> templatePanoramas = panoramaRepository.findDetailsByBoothId(fixture.templateId());

        assertEquals(2, appliedPanoramas.size());
        assertTrue(appliedPanoramas.stream().allMatch(p -> Boolean.TRUE.equals(p.getIsTemplateDerived())));
        assertTrue(templatePanoramas.stream().allMatch(p -> Boolean.FALSE.equals(p.getIsTemplateDerived())));
        assertEquals(Set.of("shared/entrance", "shared/main"), imageKeys(appliedPanoramas));
        assertEquals(imageKeys(templatePanoramas), imageKeys(appliedPanoramas));
        assertTrue(appliedPanoramas.stream().noneMatch(p -> fixture.templatePanoramaIds().contains(p.getId())));

        Panorama appliedEntrance = panoramaNamed(appliedPanoramas, "Entrance");
        Panorama appliedMain = panoramaNamed(appliedPanoramas, "Main");
        assertEquals(1, appliedEntrance.getHotspots().size());
        assertEquals(appliedMain.getId(), appliedEntrance.getHotspots().get(0).getTargetPanorama().getId());
        assertNotEquals(fixture.templateMainPanoramaId(),
                appliedEntrance.getHotspots().get(0).getTargetPanorama().getId());
    }

    private Fixture persistFixture() {
        User admin = persist(User.builder()
                .email("template-admin@example.com")
                .role(Role.ADMIN)
                .build());
        User exhibitor = persist(User.builder()
                .email("template-exhibitor@example.com")
                .role(Role.EXHIBITOR)
                .build());
        Company company = persist(Company.builder()
                .ownerUser(exhibitor)
                .name("Template Integration Company")
                .build());
        PackageTemplate packageTemplate = persist(PackageTemplate.builder()
                .createdBy(admin)
                .name("Template Integration Package")
                .description("Integration test package")
                .price(BigDecimal.TEN)
                .currency("VND")
                .maxProductsPerBooth(5)
                .maxEmbeddedVideosPerBooth(2)
                .maxPanoramasPerBooth(2)
                .maxHotspotsPerBooth(3)
                .storageLimitMb(100L)
                .listingPriority(BoothListingPriority.NORMAL)
                .build());
        Exhibition exhibition = persist(Exhibition.builder()
                .organizer(admin)
                .name("Template Integration Exhibition")
                .category("Technology")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .estimatedBooths(10)
                .status(ExhibitionStatus.REGISTRATION)
                .build());
        ExhibitionPackage exhibitionPackage = persist(ExhibitionPackage.builder()
                .template(packageTemplate)
                .exhibition(exhibition)
                .finalPrice(BigDecimal.TEN)
                .status(ExhibitionPackageStatus.ACTIVE)
                .build());
        ExhibitorRegistration registration = persist(ExhibitorRegistration.builder()
                .exhibitionPackage(exhibitionPackage)
                .company(exhibitor)
                .status(ExhibitorRegistrationStatus.APPROVED)
                .maxPanoramasPerBoothSnapshot(2)
                .maxHotspotsPerBoothSnapshot(3)
                .build());
        Booth booth = persist(Booth.builder()
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .createdBy(exhibitor)
                .company(company)
                .exhibitorRegistration(registration)
                .build());
        Booth template = persist(Booth.builder()
                .name("Published Template")
                .status(BoothStatus.PUBLISHED)
                .isTemplate(true)
                .createdBy(admin)
                .build());
        Panorama entrance = persist(Panorama.builder()
                .booth(template)
                .name("Entrance")
                .imageUrl("https://cdn/entrance.jpg")
                .imageKey("shared/entrance")
                .orderIndex(0)
                .isDefault(true)
                .build());
        Panorama main = persist(Panorama.builder()
                .booth(template)
                .name("Main")
                .imageUrl("https://cdn/main.jpg")
                .imageKey("shared/main")
                .orderIndex(1)
                .isDefault(false)
                .build());
        Hotspot hotspot = persist(Hotspot.builder()
                .type(HotspotType.NAV)
                .name("Go main")
                .sourcePanorama(entrance)
                .targetPanorama(main)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build());
        entrance.getHotspots().add(hotspot);
        template.getPanoramas().addAll(List.of(entrance, main));

        return new Fixture(
                exhibitor,
                company,
                booth.getId(),
                template.getId(),
                Set.of(entrance.getId(), main.getId()),
                main.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private Set<String> imageKeys(List<Panorama> panoramas) {
        return panoramas.stream().map(Panorama::getImageKey).collect(Collectors.toSet());
    }

    private Panorama panoramaNamed(List<Panorama> panoramas, String name) {
        return panoramas.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElseThrow();
    }

    private record Fixture(
            User exhibitor,
            Company company,
            UUID boothId,
            UUID templateId,
            Set<UUID> templatePanoramaIds,
            UUID templateMainPanoramaId) {
    }
}
