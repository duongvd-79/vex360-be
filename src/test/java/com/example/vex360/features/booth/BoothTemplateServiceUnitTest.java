package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.response.BoothTemplateSummaryResponseDTO;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.features.booth.dtos.request.CreateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.UpdateBoothTemplateRequest;
import com.example.vex360.features.booth.dtos.request.CreateHotspotRequest;
import com.example.vex360.features.booth.dtos.request.CreatePanoramaRequest;
import com.example.vex360.features.booth.dtos.response.BoothTemplateResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothTemplateService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.enums.UserStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;
import com.example.vex360.shared.utils.FileUploadUtils;

@ExtendWith(MockitoExtension.class)
class BoothTemplateServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private PanoramaRepository panoramaRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private CloudService cloudService;

    @Mock
    private PanoramaImageCleanupService panoramaImageCleanupService;

    private BoothTemplateService boothTemplateService;
    private User admin;

    @BeforeEach
    void setup() {
        boothTemplateService = new BoothTemplateService(
                boothRepository,
                panoramaRepository,
                hotspotRepository,
                cloudService,
                panoramaImageCleanupService,
                Mappers.getMapper(BoothMapper.class));
        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void createTemplateWithOnePanoramaSucceeds() {
        mockSaveFlow();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template A",
                "Demo",
                null,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1")));

        assertEquals("Template A", response.getName());
        assertSame(BoothStatus.DRAFT, response.getStatus());
        assertEquals(true, response.getIsTemplate());
        assertEquals(1, response.getPanoramas().size());
        assertEquals("Entrance", response.getPanoramas().get(0).getName());
        assertEquals(true, response.getPanoramas().get(0).getIsDefault());
        assertEquals(0, response.getPanoramas().get(0).getHotspots().size());
    }

    @Test
    void createTemplateWithPublishedStatusSucceeds() {
        mockSaveFlow();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template Published",
                "Demo",
                BoothStatus.PUBLISHED,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, null)));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1")));

        assertSame(BoothStatus.PUBLISHED, response.getStatus());
        assertEquals(true, response.getIsTemplate());
    }

    @Test
    void createTemplateWithConnectedPanoramasSucceeds() {
        mockSaveFlow();
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template B",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest(
                                "pano_1",
                                "file_1",
                                "Entrance",
                                0,
                                true,
                                List.of(new CreateHotspotRequest("Go main", "pano_2", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", 1, false, List.of())));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2")));

        assertEquals(2, response.getPanoramas().size());
        assertEquals(1, response.getPanoramas().get(0).getHotspots().size());
        assertSame(HotspotType.NAV, response.getPanoramas().get(0).getHotspots().get(0).getType());
        assertEquals("Main", response.getPanoramas().get(0).getHotspots().get(0).getTargetPanoramaName());
        assertNull(response.getPanoramas().get(0).getHotspots().get(0).getProduct());
        assertNull(response.getPanoramas().get(0).getHotspots().get(0).getMediaAsset());
        assertNull(response.getPanoramas().get(0).getHotspots().get(0).getInfoText());
    }

    @Test
    void createTemplateThrowsWhenStatusCannotBeCreated() {
        for (BoothStatus status : List.of(BoothStatus.PENDING, BoothStatus.ARCHIVED)) {
            CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                    "Template",
                    null,
                    status,
                    List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, null)));

            AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                    admin,
                    request,
                    Map.of("file_1", image("file_1"))));

            assertSame(ErrorCode.BOOTH_TEMPLATE_STATUS_INVALID, exception.getErrorCode());
        }

        verify(cloudService, never()).uploadToFolder(any(MultipartFile.class), eq(FileUploadUtils.PANORAMA_FOLDER));
    }

    @Test
    void createTemplateThrowsWhenMultiplePanoramasHaveNoHotspot() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template C",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, List.of()),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_NAVIGATION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenHotspotTargetMissing() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template D",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest(
                                "pano_1",
                                "file_1",
                                "Entrance",
                                null,
                                true,
                                List.of(new CreateHotspotRequest("Broken", "missing", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_HOTSPOT_INVALID, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenMultipleDefaultPanoramas() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template E",
                null,
                BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true, List.of()),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, true, List.of())));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_DEFAULT_PANORAMA_INVALID, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenPanoramaFileMissing() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template F",
                null,
                BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of()));

        assertSame(ErrorCode.PANORAMA_FILE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createTemplateThrowsWhenPanoramaFileExtra() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template F",
                null,
                BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.PANORAMA_FILE_INVALID, exception.getErrorCode());
    }

    @Test
    void createTemplate_NullUser_ThrowsUnauthenticated() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                null, request, Map.of("file_1", image("file_1"))));

        assertSame(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void createTemplate_NullRequest_ThrowsInvalidBoothTemplate() {
        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, null, Map.of("file_1", image("file_1"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_NAME_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createTemplate_BlankName_ThrowsInvalidBoothTemplate() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "   ", "Desc", BoothStatus.DRAFT,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request, Map.of("file_1", image("file_1"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_NAME_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createTemplate_EmptyPanoramas_ThrowsInvalidBoothTemplate() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT, List.of());

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request, Map.of()));

        assertSame(ErrorCode.BOOTH_TEMPLATE_PANORAMA_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createTemplate_NullPanorama_ThrowsInvalidBoothTemplate() {
        List<CreatePanoramaRequest> panos = new ArrayList<>();
        panos.add(null);
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT, panos);

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request, Map.of()));

        assertSame(ErrorCode.BOOTH_TEMPLATE_PANORAMA_INVALID, exception.getErrorCode());
    }

    @Test
    void createTemplate_DuplicateClientKey_ThrowsInvalidBoothTemplate() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null),
                        new CreatePanoramaRequest("pano_1", "file_2", "Entrance 2", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request, Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_PANORAMA_KEY_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void createTemplate_DuplicateFileKey_ThrowsInvalidBoothTemplate() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null),
                        new CreatePanoramaRequest("pano_2", "file_1", "Entrance 2", null, null, null)));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request, Map.of("file_1", image("file_1"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_PANORAMA_KEY_DUPLICATED, exception.getErrorCode());
    }

    @Test
    void createTemplate_UnreachablePanorama_ThrowsInvalidPanoramaHotspot() {
        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true,
                                List.of(new CreateHotspotRequest("Go Main", "pano_2", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of()),
                        new CreatePanoramaRequest("pano_3", "file_3", "Secret Room", null, false, List.of()) // disconnected!
                ));

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.createBoothTemplate(
                admin, request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"), "file_3", image("file_3"))));

        assertSame(ErrorCode.BOOTH_TEMPLATE_PANORAMA_UNREACHABLE, exception.getErrorCode());
    }

    @Test
    void createTemplate_WhenLaterUploadFails_DeletesPreviouslyUploadedImages() {
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> {
            Booth booth = invocation.getArgument(0);
            booth.setId(UUID.randomUUID());
            return booth;
        });
        when(cloudService.uploadToFolder(any(MultipartFile.class), eq(FileUploadUtils.PANORAMA_FOLDER)))
                .thenReturn(CloudinaryResponse.builder()
                        .url("https://res.cloudinary.com/demo/image/upload/panorama/file_1.jpg")
                        .publicId("panorama/file_1.jpg")
                        .fileName("file_1.jpg")
                        .fileSize(5L)
                        .fileType("image/jpeg")
                        .build())
                .thenThrow(new RuntimeException("upload failed"));

        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template", "Desc", BoothStatus.DRAFT,
                List.of(
                        new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, true,
                                List.of(new CreateHotspotRequest("Go Main", "pano_2", 1.0, 2.0, 3.0))),
                        new CreatePanoramaRequest("pano_2", "file_2", "Main", null, false, List.of())));

        assertThrows(RuntimeException.class, () -> boothTemplateService.createBoothTemplate(
                admin,
                request,
                Map.of("file_1", image("file_1"), "file_2", image("file_2"))));

        verify(cloudService).delete("panorama/file_1.jpg", "image");
        verify(panoramaRepository, never()).saveAll(any());
        verify(hotspotRepository, never()).saveAll(any());
    }

    @Test
    void getBoothTemplates_Succeeds() {
        PageRequest pageable = PageRequest.of(0, 10);
        Booth booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Template A")
                .isTemplate(true)
                .status(BoothStatus.PUBLISHED)
                .build();
        Page<Booth> page = new PageImpl<>(List.of(booth), pageable, 1);

        when(boothRepository.searchTemplates("Template", BoothStatus.PUBLISHED, pageable)).thenReturn(page);

        PageResponse<BoothTemplateSummaryResponseDTO> response = boothTemplateService.getBoothTemplates(" Template ",
                BoothStatus.PUBLISHED, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Template A", response.getContent().get(0).getName());
    }

    @Test
    void getBoothTemplateById_Exists_ReturnsDto() {
        UUID id = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(id)
                .name("Template A")
                .isTemplate(true)
                .status(BoothStatus.PUBLISHED)
                .build();

        when(boothRepository.findTemplateById(id)).thenReturn(Optional.of(booth));

        BoothTemplateResponseDTO response = boothTemplateService.getBoothTemplateById(id);

        assertNotNull(response);
        assertEquals("Template A", response.getName());
    }

    @Test
    void getBoothTemplateById_NotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(boothRepository.findTemplateById(id)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.getBoothTemplateById(id));
        assertSame(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteBoothTemplate_DraftTemplate_DeletesBoothAndCloudImages() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.DRAFT);
        booth.setThumbnailPublicId("template/thumb.jpg");
        booth.setPanoramas(List.of(
                panorama(booth, "Entrance", "panorama/entrance.jpg"),
                panorama(booth, "Main", "panorama/main.jpg")));
        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));

        BoothTemplateResponseDTO response = boothTemplateService.deleteBoothTemplate(id);

        assertEquals(booth.getId(), response.getId());
        assertEquals("Template A", response.getName());
        assertEquals(2, response.getPanoramas().size());
        verify(boothRepository).delete(booth);
        verify(panoramaImageCleanupService).scheduleCleanup(List.of(
                "template/thumb.jpg", "panorama/entrance.jpg", "panorama/main.jpg"));
    }

    @Test
    void deleteBoothTemplate_DraftTemplate_SkipsBlankImageKeys() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.DRAFT);
        booth.setThumbnailPublicId(null);
        booth.setPanoramas(List.of(
                panorama(booth, "Entrance", ""),
                panorama(booth, "Main", "   ")));
        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));

        boothTemplateService.deleteBoothTemplate(id);

        verify(boothRepository).delete(booth);
        verify(panoramaImageCleanupService).scheduleCleanup(any(List.class));
    }

    @Test
    void deleteBoothTemplate_NonDraftTemplate_ThrowsBoothNotEditable() {
        for (BoothStatus status : List.of(BoothStatus.PENDING, BoothStatus.PUBLISHED)) {
            UUID id = UUID.randomUUID();
            when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(templateBooth(status)));

            AppException exception = assertThrows(AppException.class,
                    () -> boothTemplateService.deleteBoothTemplate(id));

            assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
        }

        verify(boothRepository, never()).delete(any(Booth.class));
        verify(cloudService, never()).delete(any(), eq("image"));
    }

    @Test
    void deleteBoothTemplate_NotFound_ThrowsBoothTemplateNotFound() {
        UUID id = UUID.randomUUID();
        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> boothTemplateService.deleteBoothTemplate(id));

        assertSame(ErrorCode.BOOTH_TEMPLATE_NOT_FOUND, exception.getErrorCode());
        verify(boothRepository, never()).delete(any(Booth.class));
        verify(cloudService, never()).delete(any(), eq("image"));
    }

    private void mockSaveFlow() {
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> {
            Booth booth = invocation.getArgument(0);
            booth.setId(UUID.randomUUID());
            return booth;
        });

        when(cloudService.uploadToFolder(any(MultipartFile.class), eq(FileUploadUtils.PANORAMA_FOLDER)))
                .thenAnswer(invocation -> {
                    MultipartFile file = invocation.getArgument(0);
                    String publicId = FileUploadUtils.PANORAMA_FOLDER + "/" + file.getOriginalFilename();
                    return CloudinaryResponse.builder()
                            .url("https://res.cloudinary.com/demo/image/upload/" + publicId)
                            .publicId(publicId)
                            .fileName(file.getOriginalFilename())
                            .fileSize(file.getSize())
                            .fileType(file.getContentType())
                            .build();
                });

        when(panoramaRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Panorama> panoramas = new ArrayList<>((List<Panorama>) invocation.getArgument(0));
            panoramas.forEach(panorama -> panorama.setId(UUID.randomUUID()));
            return panoramas;
        });

        when(hotspotRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Hotspot> hotspots = new ArrayList<>((List<Hotspot>) invocation.getArgument(0));
            hotspots.forEach(hotspot -> hotspot.setId(UUID.randomUUID()));
            return hotspots;
        });
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", "image".getBytes());
    }

    @Test
    void createTemplateWithThumbnailSucceeds() {
        mockSaveFlow();
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "thumb.jpg", "image/jpeg",
                "thumbnail-data".getBytes());
        CloudinaryResponse cloudResponse = CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/thumb.jpg")
                .publicId("template/thumb")
                .fileName("image")
                .fileSize(1234L)
                .fileType("jpg")
                .build();
        when(cloudService.upload(thumbnail)).thenReturn(cloudResponse);

        CreateBoothTemplateRequest request = new CreateBoothTemplateRequest(
                "Template A",
                "Demo",
                null,
                List.of(new CreatePanoramaRequest("pano_1", "file_1", "Entrance", null, null, null)));

        BoothTemplateResponseDTO response = boothTemplateService.createBoothTemplate(
                admin,
                request,
                thumbnail,
                Map.of("file_1", image("file_1")));

        assertNotNull(response);
        assertEquals("Template A", response.getName());
        assertEquals("https://res.cloudinary.com/thumb.jpg", response.getThumbnailUrl());
        verify(cloudService).upload(thumbnail);
    }

    @Test
    void updateBoothTemplate_DraftStatus_MetadataAndThumbnailSucceeds() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.DRAFT);
        booth.setThumbnailPublicId("template/old_thumb");

        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile newThumbnail = new MockMultipartFile("thumbnail", "new_thumb.jpg", "image/jpeg",
                "new-thumbnail-data".getBytes());
        CloudinaryResponse uploadResponse = CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/new_thumb.jpg")
                .publicId("template/new_thumb")
                .fileName("image")
                .fileSize(1234L)
                .fileType("jpg")
                .build();
        when(cloudService.upload(newThumbnail)).thenReturn(uploadResponse);

        UpdateBoothTemplateRequest updateRequest = new UpdateBoothTemplateRequest("Updated Name", "Updated Desc",
                BoothStatus.PUBLISHED);

        BoothTemplateResponseDTO response = boothTemplateService.updateBoothTemplate(admin, id, updateRequest,
                newThumbnail);

        assertNotNull(response);
        assertEquals("Updated Name", response.getName());
        assertEquals("Updated Desc", response.getDescription());
        assertEquals(BoothStatus.PUBLISHED, response.getStatus());
        assertEquals("https://res.cloudinary.com/new_thumb.jpg", response.getThumbnailUrl());
        verify(cloudService).delete("template/old_thumb", "image"); // Xóa vì đang ở trạng thái DRAFT
    }

    @Test
    void updateBoothTemplate_PublishedStatus_TransitionToArchivedSucceeds() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.PUBLISHED);

        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBoothTemplateRequest updateRequest = new UpdateBoothTemplateRequest(null, null, BoothStatus.ARCHIVED);

        BoothTemplateResponseDTO response = boothTemplateService.updateBoothTemplate(admin, id, updateRequest, null);

        assertNotNull(response);
        assertEquals(BoothStatus.ARCHIVED, response.getStatus());
    }

    @Test
    void updateBoothTemplate_PublishedStatus_EditFieldsThrowsException() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.PUBLISHED);

        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));

        UpdateBoothTemplateRequest updateRequest = new UpdateBoothTemplateRequest("New Name", null, null);

        assertThrows(AppException.class,
                () -> boothTemplateService.updateBoothTemplate(admin, id, updateRequest, null));
    }

    @Test
    void updateBoothTemplate_ArchivedStatus_EditFieldsAndThumbnailSucceedsWithoutDeletingOld() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.ARCHIVED);
        booth.setThumbnailPublicId("template/old_thumb");

        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile newThumbnail = new MockMultipartFile("thumbnail", "new_thumb.jpg", "image/jpeg",
                "new-thumbnail-data".getBytes());
        CloudinaryResponse uploadResponse = CloudinaryResponse.builder()
                .url("https://res.cloudinary.com/new_thumb.jpg")
                .publicId("template/new_thumb")
                .fileName("image")
                .fileSize(1234L)
                .fileType("jpg")
                .build();
        when(cloudService.upload(newThumbnail)).thenReturn(uploadResponse);

        UpdateBoothTemplateRequest updateRequest = new UpdateBoothTemplateRequest("New Name", "New Desc",
                BoothStatus.PUBLISHED);

        BoothTemplateResponseDTO response = boothTemplateService.updateBoothTemplate(admin, id, updateRequest,
                newThumbnail);

        assertNotNull(response);
        assertEquals("New Name", response.getName());
        assertEquals("New Desc", response.getDescription());
        assertEquals(BoothStatus.PUBLISHED, response.getStatus());
        assertEquals("https://res.cloudinary.com/new_thumb.jpg", response.getThumbnailUrl());
        verify(cloudService, never()).delete("template/old_thumb", "image"); // Không xóa khi ở trạng thái ARCHIVED
    }

    @Test
    void updateBoothTemplate_ArchivedStatus_TransitionToDraftThrowsException() {
        UUID id = UUID.randomUUID();
        Booth booth = templateBooth(BoothStatus.ARCHIVED);

        when(boothRepository.findTemplateByIdForUpdate(id)).thenReturn(Optional.of(booth));

        UpdateBoothTemplateRequest updateRequest = new UpdateBoothTemplateRequest(null, null, BoothStatus.DRAFT);

        assertThrows(AppException.class,
                () -> boothTemplateService.updateBoothTemplate(admin, id, updateRequest, null));
    }

    private Booth templateBooth(BoothStatus status) {
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Template A")
                .description("Demo")
                .status(status)
                .isTemplate(true)
                .createdBy(admin)
                .build();
    }

    private Panorama panorama(Booth booth, String name, String imageKey) {
        return Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name(name)
                .imageUrl("https://res.cloudinary.com/demo/image/upload/" + imageKey)
                .imageKey(imageKey)
                .orderIndex(0)
                .isDefault(false)
                .build();
    }

}
