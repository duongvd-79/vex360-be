package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapperImpl;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothBenefitGuardService;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.services.CloudService;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=false"
})
@Import({ ExhibitorPanoramaService.class, BoothMapperImpl.class })
class ExhibitorPanoramaDeletionJpaIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ExhibitorPanoramaService service;
    @Autowired
    private PanoramaRepository panoramaRepository;
    @Autowired
    private HotspotRepository hotspotRepository;

    @MockitoBean
    private CompanyService companyService;
    @MockitoBean
    private CloudService cloudService;
    @MockitoBean
    private PanoramaImageCleanupService panoramaImageCleanupService;
    @MockitoBean
    private BoothBenefitGuardService boothBenefitGuardService;
    @MockitoBean
    private BoothReviewPolicyService boothReviewPolicyService;

    @Test
    void deletePanoramaRemovesIncomingOutgoingAndSelfReferencingHotspots() {
        Fixture fixture = persistFixture();
        Panorama entrance = panorama(fixture.booth(), "Entrance", "booth/entrance", 0, true);
        Panorama main = panorama(fixture.booth(), "Main", "booth/main", 1, false);
        Hotspot incoming = hotspot(HotspotType.NAV, "Main to entrance", main, entrance);
        Hotspot outgoing = hotspot(HotspotType.INFO, "Entrance info", entrance, null);
        Hotspot selfReference = hotspot(HotspotType.NAV, "Entrance loop", entrance, entrance);
        entityManager.flush();
        entityManager.clear();
        when(companyService.getCompanyEntityForCurrentUser(fixture.user())).thenReturn(fixture.company());

        service.deletePanorama(fixture.user(), fixture.booth().getId(), entrance.getId());

        entityManager.clear();
        assertFalse(panoramaRepository.existsById(entrance.getId()));
        assertTrue(panoramaRepository.existsById(main.getId()));
        assertFalse(hotspotRepository.existsById(incoming.getId()));
        assertFalse(hotspotRepository.existsById(outgoing.getId()));
        assertFalse(hotspotRepository.existsById(selfReference.getId()));
        assertEquals(0, hotspotRepository.count());
    }

    @Test
    void deleteAllPanoramasRemovesCircularLinksAndAllHotspotTypes() {
        Fixture fixture = persistFixture();
        Panorama entrance = panorama(fixture.booth(), "Entrance", "booth/entrance", 0, true);
        Panorama main = panorama(fixture.booth(), "Main", "booth/main", 1, false);
        hotspot(HotspotType.NAV, "Entrance to main", entrance, main);
        hotspot(HotspotType.NAV, "Main to entrance", main, entrance);
        hotspot(HotspotType.INFO, "Info", entrance, null);
        hotspot(HotspotType.MEDIA, "Media", main, null);
        hotspot(HotspotType.PRODUCT, "Product", main, null);
        entityManager.flush();
        entityManager.clear();
        when(companyService.getCompanyEntityForCurrentUser(fixture.user())).thenReturn(fixture.company());

        service.deleteAllPanoramas(fixture.user(), fixture.booth().getId());

        entityManager.clear();
        assertEquals(0, panoramaRepository.countByBoothId(fixture.booth().getId()));
        assertEquals(0, hotspotRepository.count());
        verify(panoramaImageCleanupService).scheduleCleanup(Set.of("booth/entrance", "booth/main"));
    }

    private Fixture persistFixture() {
        User user = persist(User.builder()
                .email("panorama-delete@example.com")
                .role(Role.EXHIBITOR)
                .status(UserStatus.ACTIVE)
                .build());
        Company company = persist(Company.builder()
                .ownerUser(user)
                .name("Panorama Delete Company")
                .build());
        Booth booth = persist(Booth.builder()
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .createdBy(user)
                .company(company)
                .build());
        return new Fixture(user, company, booth);
    }

    private Panorama panorama(
            Booth booth,
            String name,
            String imageKey,
            int orderIndex,
            boolean isDefault) {
        return persist(Panorama.builder()
                .booth(booth)
                .name(name)
                .imageUrl("https://cdn/" + name.toLowerCase() + ".jpg")
                .imageKey(imageKey)
                .orderIndex(orderIndex)
                .isDefault(isDefault)
                .build());
    }

    private Hotspot hotspot(HotspotType type, String name, Panorama source, Panorama target) {
        return persist(Hotspot.builder()
                .type(type)
                .name(name)
                .sourcePanorama(source)
                .targetPanorama(target)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Fixture(User user, Company company, Booth booth) {
    }
}
