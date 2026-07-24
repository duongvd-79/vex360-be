package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.dtos.request.UpdateExhibitorPanoramaRequest;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothBenefitGuardService;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorPanoramaService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.designrequest.repositories.DesignDraftAssetRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitorPanoramaServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private CompanyService companyService;
    @Mock
    private CompanyStorageService companyStorageService;

    @Mock
    private CloudService cloudService;

    @Mock
    private PanoramaImageCleanupService panoramaImageCleanupService;

    @Mock
    private BoothBenefitGuardService boothBenefitGuardService;

    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    @Mock
    private DesignDraftAssetRepository designDraftAssetRepository;

    private ExhibitorPanoramaService exhibitorPanoramaService;
    private User exhibitorUser;
    private Company company;
    private Booth booth;

    @BeforeEach
    void setup() {
        exhibitorPanoramaService = new ExhibitorPanoramaService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                companyService,
                companyStorageService,
                cloudService,
                panoramaImageCleanupService,
                Mappers.getMapper(BoothMapper.class),
                boothBenefitGuardService,
                boothReviewPolicyService,
                designDraftAssetRepository);
        exhibitorUser = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitorUser).name("VEX Company").build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Runtime Booth")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .createdBy(exhibitorUser)
                .build();
    }

    @Test
    void createPanorama_WhenBoothIsDesigning_DoesNotUploadImage() {
        booth.setStatus(BoothStatus.DESIGNING);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "panorama.jpg",
                "image/jpeg",
                "image".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_NOT_EDITABLE))
                .when(boothReviewPolicyService).assertEditable(booth);

        AppException exception = assertThrows(AppException.class, () -> exhibitorPanoramaService.createPanorama(
                exhibitorUser,
                booth.getId(),
                new CreateExhibitorPanoramaRequest("Entrance", null, true),
                image));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void createPanorama_WhenQuotaExceeded_DoesNotUploadImage() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "panorama.jpg",
                "image/jpeg",
                "image".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_QUOTA_EXCEEDED))
                .when(boothBenefitGuardService)
                .assertCanAddPanorama(booth);

        AppException exception = assertThrows(AppException.class, () -> exhibitorPanoramaService.createPanorama(
                exhibitorUser,
                booth.getId(),
                new CreateExhibitorPanoramaRequest("Entrance", null, true),
                image));

        assertSame(ErrorCode.BOOTH_QUOTA_EXCEEDED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void createPanoramaStoresActualSizeAndChargesQuotaAfterSave() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "panorama.jpg", "image/jpeg", "image".getBytes());
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId())).thenReturn(List.of());
        when(cloudService.uploadToFolder(any(), any())).thenReturn(CloudinaryResponse.builder()
                .url("https://cdn/new.jpg")
                .publicId("booth/new")
                .fileSize(25L)
                .build());
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(invocation -> invocation.getArgument(0));

        exhibitorPanoramaService.createPanorama(
                exhibitorUser,
                booth.getId(),
                new CreateExhibitorPanoramaRequest("Entrance", null, true),
                image);

        ArgumentCaptor<Panorama> captor = ArgumentCaptor.forClass(Panorama.class);
        verify(panoramaRepository).saveAndFlush(captor.capture());
        assertEquals(25L, captor.getValue().getFileSize());
        verify(companyStorageService).addUsage(company, 25L);
    }

    @Test
    void updateOwnedPanoramaReconcilesOnlyTheSizeDifference() {
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = panorama(panoramaId, "Entrance", "booth/old", 0);
        panorama.setFileSize(10L);
        MockMultipartFile image = new MockMultipartFile(
                "image", "new.jpg", "image/jpeg", "new-image".getBytes());
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId()))
                .thenReturn(Optional.of(panorama));
        when(cloudService.uploadToFolder(any(), any())).thenReturn(CloudinaryResponse.builder()
                .url("https://cdn/new.jpg")
                .publicId("booth/new")
                .fileSize(20L)
                .build());
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(invocation -> invocation.getArgument(0));

        exhibitorPanoramaService.updatePanorama(
                exhibitorUser,
                booth.getId(),
                panoramaId,
                new UpdateExhibitorPanoramaRequest(null, null, null),
                image);

        verify(companyStorageService).reconcileUsage(company, 10L, 20L, 0L);
        verify(panoramaImageCleanupService).scheduleCleanup("booth/old");
    }

    @Test
    void updateTemplateDerivedPanoramaUsesNewOwnedImageAndSchedulesOldCleanup() {
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = Panorama.builder()
                .id(panoramaId)
                .booth(booth)
                .name("Entrance")
                .imageUrl("https://cdn/old.jpg")
                .imageKey("template/old")
                .orderIndex(0)
                .isDefault(true)
                .isTemplateDerived(true)
                .build();
        MockMultipartFile image = new MockMultipartFile(
                "image", "new.jpg", "image/jpeg", "new-image".getBytes());
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId()))
                .thenReturn(Optional.of(panorama));
        when(cloudService.uploadToFolder(any(), any())).thenReturn(CloudinaryResponse.builder()
                .url("https://cdn/new.jpg")
                .publicId("booth/new")
                .build());
        when(panoramaRepository.saveAndFlush(any(Panorama.class))).thenAnswer(invocation -> invocation.getArgument(0));

        exhibitorPanoramaService.updatePanorama(
                exhibitorUser,
                booth.getId(),
                panoramaId,
                new UpdateExhibitorPanoramaRequest(null, null, null),
                image);

        ArgumentCaptor<Panorama> captor = ArgumentCaptor.forClass(Panorama.class);
        verify(panoramaRepository).saveAndFlush(captor.capture());
        assertFalse(captor.getValue().getIsTemplateDerived());
        verify(panoramaImageCleanupService).scheduleCleanup("template/old");
        verify(cloudService, never()).delete("template/old", "image");
    }

    @Test
    void deleteTemplateDerivedPanoramaSchedulesReferenceAwareCleanup() {
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = Panorama.builder()
                .id(panoramaId)
                .booth(booth)
                .name("Entrance")
                .imageUrl("https://cdn/shared.jpg")
                .imageKey("template/shared")
                .orderIndex(0)
                .isDefault(true)
                .isTemplateDerived(true)
                .build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId()))
                .thenReturn(Optional.of(panorama));

        exhibitorPanoramaService.deletePanorama(exhibitorUser, booth.getId(), panoramaId);

        verify(panoramaRepository).delete(panorama);
        verify(companyStorageService, never()).deductUsage(any(), any(Long.class));
        verify(panoramaImageCleanupService).scheduleCleanup("template/shared");
        verify(cloudService, never()).delete("template/shared", "image");
    }

    @Test
    void deletePanorama_RemovesIncomingHotspotsBeforePanorama() {
        UUID panoramaId = UUID.randomUUID();
        Panorama panorama = Panorama.builder()
                .id(panoramaId)
                .booth(booth)
                .name("Entrance")
                .imageUrl("https://cdn/entrance.jpg")
                .imageKey("booth/entrance")
                .fileSize(15L)
                .orderIndex(0)
                .isDefault(true)
                .build();
        Hotspot incoming = Hotspot.builder().id(UUID.randomUUID()).name("Go entrance").build();
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(panoramaRepository.findByIdAndBoothIdForUpdate(panoramaId, booth.getId()))
                .thenReturn(Optional.of(panorama));
        when(hotspotRepository.findAllByTargetPanoramaIdIn(List.of(panoramaId))).thenReturn(List.of(incoming));

        exhibitorPanoramaService.deletePanorama(exhibitorUser, booth.getId(), panoramaId);

        InOrder order = inOrder(hotspotRepository, panoramaRepository);
        order.verify(hotspotRepository).deleteAll(List.of(incoming));
        order.verify(hotspotRepository).flush();
        order.verify(panoramaRepository).delete(panorama);
        order.verify(panoramaRepository).flush();
        verify(companyStorageService).deductUsage(company, 15L);
    }

    @Test
    void deleteAllPanoramas_RemovesAllContentAndSchedulesCleanup() {
        Panorama entrance = panorama(UUID.randomUUID(), "Entrance", "booth/entrance", 0);
        Panorama main = panorama(UUID.randomUUID(), "Main", "booth/main", 1);
        Hotspot incoming = Hotspot.builder().id(UUID.randomUUID()).name("Navigate").build();
        List<UUID> panoramaIds = List.of(entrance.getId(), main.getId());
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId()))
                .thenReturn(List.of(entrance, main));
        when(hotspotRepository.findAllByTargetPanoramaIdIn(panoramaIds)).thenReturn(List.of(incoming));

        exhibitorPanoramaService.deleteAllPanoramas(exhibitorUser, booth.getId());

        InOrder order = inOrder(hotspotRepository, panoramaRepository);
        order.verify(hotspotRepository).deleteAll(List.of(incoming));
        order.verify(hotspotRepository).flush();
        order.verify(panoramaRepository).deleteAll(List.of(entrance, main));
        order.verify(panoramaRepository).flush();
        verify(companyStorageService).deductUsage(company, 20L);
        verify(panoramaImageCleanupService).scheduleCleanup(Set.of("booth/entrance", "booth/main"));
    }

    @Test
    void deleteAllPanoramas_EmptyBoothThrowsPanoramaNotFound() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(panoramaRepository.findByBoothIdOrderByOrderIndexAsc(booth.getId())).thenReturn(List.of());

        AppException exception = assertThrows(
                AppException.class,
                () -> exhibitorPanoramaService.deleteAllPanoramas(exhibitorUser, booth.getId()));

        assertSame(ErrorCode.PANORAMA_NOT_FOUND, exception.getErrorCode());
        verify(hotspotRepository, never()).deleteAll(any());
        verify(panoramaRepository, never()).deleteAll(any());
    }

    @Test
    void deleteAllPanoramas_NonDraftBoothDoesNotDeleteContent() {
        booth.setStatus(BoothStatus.PENDING);
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_NOT_EDITABLE))
                .when(boothReviewPolicyService).assertEditable(booth);

        AppException exception = assertThrows(
                AppException.class,
                () -> exhibitorPanoramaService.deleteAllPanoramas(exhibitorUser, booth.getId()));

        assertEquals(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
        verify(panoramaRepository, never()).findByBoothIdOrderByOrderIndexAsc(any());
    }

    @Test
    void deleteAllPanoramas_BoothOutsideCompanyThrowsBoothNotFound() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> exhibitorPanoramaService.deleteAllPanoramas(exhibitorUser, booth.getId()));

        assertEquals(ErrorCode.BOOTH_NOT_FOUND, exception.getErrorCode());
        verify(panoramaRepository, never()).findByBoothIdOrderByOrderIndexAsc(any());
    }

    private Panorama panorama(UUID id, String name, String imageKey, int orderIndex) {
        return Panorama.builder()
                .id(id)
                .booth(booth)
                .name(name)
                .imageUrl("https://cdn/" + name.toLowerCase() + ".jpg")
                .imageKey(imageKey)
                .fileSize(10L)
                .orderIndex(orderIndex)
                .isDefault(orderIndex == 0)
                .build();
    }
}
