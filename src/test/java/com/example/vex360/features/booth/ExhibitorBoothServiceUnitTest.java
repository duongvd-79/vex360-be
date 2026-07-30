package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.features.booth.dtos.request.UpdateBoothRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.ExhibitorBoothService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitorBoothServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private CloudService cloudService;

    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    @Mock
    private PanoramaImageCleanupService assetCleanupService;

    private ExhibitorBoothService exhibitorBoothService;
    private User exhibitorUser;
    private Company company;

    @BeforeEach
    void setup() {
        exhibitorBoothService = new ExhibitorBoothService(
                boothRepository,
                companyService,
                cloudService,
                Mappers.getMapper(BoothMapper.class),
                boothReviewPolicyService,
                assetCleanupService);
        exhibitorUser = User.builder()
                .id(UUID.randomUUID())
                .email("exhibitor@example.com")
                .build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .ownerUser(exhibitorUser)
                .name("VEX Company")
                .build();
    }

    @Test
    void updateBoothReplacesThumbnailAndDeletesOldCloudinaryFile() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(boothId)
                .name("Old Booth")
                .description("Old description")
                .status(BoothStatus.DRAFT)
                .isTemplate(false)
                .company(company)
                .thumbnailUrl("https://old.example/thumbnail.png")
                .thumbnailPublicId("old_public_id")
                .build();
        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail",
                "thumbnail.png",
                "image/png",
                "image".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://new.example/thumbnail.png")
                .publicId("new_public_id")
                .fileType("image/png")
                .build();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(cloudService.upload(thumbnail)).thenReturn(upload);
        when(boothRepository.save(any(Booth.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BoothResponseDTO response = exhibitorBoothService.updateBooth(
                exhibitorUser,
                boothId,
                new UpdateBoothRequest("New Booth", "", null),
                thumbnail,
                null);

        assertEquals("New Booth", response.getName());
        assertEquals("https://new.example/thumbnail.png", response.getThumbnailUrl());
        verify(assetCleanupService).scheduleCleanup("old_public_id", "image");
    }

    @Test
    void updateBooth_WhenDesigning_ThrowsExceptionBeforeUpload() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(boothId)
                .name("Locked Booth")
                .company(company)
                .status(BoothStatus.DESIGNING)
                .build();
        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnail",
                "thumbnail.png",
                "image/png",
                "image".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_NOT_EDITABLE))
                .when(boothReviewPolicyService).assertMetadataEditable(booth);

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(
                        exhibitorUser,
                        boothId,
                        new UpdateBoothRequest("New Booth", null, null),
                        thumbnail,
                        null));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, ex.getErrorCode());
        verify(cloudService, never()).upload(any());
        verify(boothRepository, never()).save(any());
    }

    @Test
    void getBooths_ReturnsPageOfBoothResponseDTO() {
        PageRequest pageable = PageRequest.of(0, 10);
        Booth booth = Booth.builder().id(UUID.randomUUID()).name("Booth A").company(company).build();
        Page<Booth> page = new PageImpl<>(List.of(booth), pageable, 1);

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBooths(company.getId(), pageable)).thenReturn(page);

        PageResponse<BoothResponseDTO> response = exhibitorBoothService.getBooths(exhibitorUser, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Booth A", response.getContent().get(0).getName());
    }

    @Test
    void getBoothById_Exists_ReturnsBoothResponseDTO() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth A").company(company).build();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        BoothResponseDTO response = exhibitorBoothService.getBoothById(exhibitorUser, boothId);

        assertNotNull(response);
        assertEquals("Booth A", response.getName());
    }

    @Test
    void getBoothById_NotFound_ThrowsException() {
        UUID boothId = UUID.randomUUID();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorBoothService.getBoothById(exhibitorUser, boothId));
        assertSame(ErrorCode.BOOTH_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateBooth_MetadataOnly_Succeeds() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Old Booth").description("Old Desc").company(company).build();
        UpdateBoothRequest request = new UpdateBoothRequest("New Name", "New Desc", "modern");

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        BoothResponseDTO response = exhibitorBoothService.updateBooth(exhibitorUser, boothId, request, null, null);

        assertEquals("New Name", response.getName());
        assertEquals("New Desc", response.getDescription());
        assertEquals("modern", booth.getDisplayTemplateKey());
    }

    @Test
    void updateBooth_BlankName_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Old Booth").company(company).build();
        UpdateBoothRequest request = new UpdateBoothRequest("   ", "Desc", "classic");

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, request, null, null));
        assertSame(ErrorCode.INVALID_BOOTH, ex.getErrorCode());
    }

    @Test
    void updateBooth_InvalidThumbnailMimeType_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Old Booth").company(company).build();
        MockMultipartFile textFile = new MockMultipartFile("thumbnail", "test.txt", "text/plain", "hello".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException ex = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, textFile, null));
        assertSame(ErrorCode.INVALID_BOOTH, ex.getErrorCode());
    }

    @Test
    void updateBooth_EmptyThumbnail_IsIgnoredAndSucceeds() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Old Booth").company(company).build();
        MockMultipartFile emptyFile = new MockMultipartFile("thumbnail", "test.png", "image/png", new byte[0]);

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        BoothResponseDTO response = exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, emptyFile, null);

        assertNotNull(response);
        assertEquals("Old Booth", response.getName());
        verify(cloudService, never()).upload(any());
    }

    @Test
    void updateBooth_DescriptionAndTemplateKeyEmpty_SetsNullAndClassic() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").description("Old").displayTemplateKey("modern")
                .company(company).build();
        UpdateBoothRequest request = new UpdateBoothRequest("New Name", "  ", "   ");

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(boothRepository.save(any(Booth.class))).thenAnswer(inv -> inv.getArgument(0));

        BoothResponseDTO response = exhibitorBoothService.updateBooth(exhibitorUser, boothId, request, null, null);

        assertEquals("New Name", response.getName());
        assertNull(booth.getDescription());
        assertEquals("classic", booth.getDisplayTemplateKey());
    }

    @Test
    void updateBooth_ReplacesBackgroundMusicAndDeletesOldCloudinaryFile() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(boothId)
                .name("Booth")
                .company(company)
                .backgroundMusicUrl("https://old.example/music.mp3")
                .backgroundMusicPublicId("old_music_id")
                .backgroundMusicFileName("old.mp3")
                .backgroundMusicFileSize(100L)
                .build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic",
                "ambient.mp3",
                "audio/mpeg",
                "music".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://new.example/ambient.mp3")
                .publicId("new_music_id")
                .fileName("ambient.mp3")
                .fileSize(5L)
                .fileType("audio/mpeg")
                .build();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(cloudService.uploadToFolder(music, "booth-background-music")).thenReturn(upload);
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothResponseDTO response = exhibitorBoothService.updateBooth(
                exhibitorUser, boothId, null, null, music);

        assertEquals("https://new.example/ambient.mp3", response.getBackgroundMusicUrl());
        assertEquals("ambient.mp3", response.getBackgroundMusicFileName());
        assertEquals(5L, response.getBackgroundMusicFileSize());
        verify(assetCleanupService).scheduleCleanup("old_music_id", "video");
    }

    @Test
    void updateBooth_BackgroundMusicWithInvalidExtension_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.wav", "audio/mpeg", "music".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        assertSame(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void updateBooth_BackgroundMusicWithInvalidMimeType_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "text/plain", "music".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        assertSame(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void updateBooth_EmptyBackgroundMusic_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", new byte[0]);

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        assertSame(ErrorCode.FILE_TYPE_NOT_SUPPORTED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void updateBooth_BackgroundMusicOverTenMegabytes_ThrowsException() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic",
                "ambient.mp3",
                "audio/mpeg",
                new byte[10 * 1024 * 1024 + 1]);

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));

        AppException exception = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        assertSame(ErrorCode.FILE_SIZE_EXCEEDED, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
    }

    @Test
    void updateBooth_WhenSaveFails_DeletesNewBackgroundMusic() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mp3", "music".getBytes());
        CloudinaryResponse upload = CloudinaryResponse.builder()
                .url("https://new.example/ambient.mp3")
                .publicId("new_music_id")
                .fileSize(5L)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(cloudService.uploadToFolder(music, "booth-background-music")).thenReturn(upload);
        when(boothRepository.save(booth)).thenThrow(new RuntimeException("database failure"));

        assertThrows(RuntimeException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        verify(cloudService).delete("new_music_id", "video");
    }

    @Test
    void updateBooth_WhenNotEditable_DoesNotUploadBackgroundMusic() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder().id(boothId).name("Booth").company(company).build();
        MockMultipartFile music = new MockMultipartFile(
                "backgroundMusic", "ambient.mp3", "audio/mpeg", "music".getBytes());

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        doThrow(new AppException(ErrorCode.BOOTH_NOT_EDITABLE))
                .when(boothReviewPolicyService).assertMetadataEditable(booth);

        AppException exception = assertThrows(AppException.class,
                () -> exhibitorBoothService.updateBooth(exhibitorUser, boothId, null, null, music));

        assertSame(ErrorCode.BOOTH_NOT_EDITABLE, exception.getErrorCode());
        verify(cloudService, never()).uploadToFolder(any(), any());
        verify(boothRepository, never()).save(any());
    }

    @Test
    void deleteBackgroundMusic_ClearsMetadataAndDeletesCloudinaryFile() {
        UUID boothId = UUID.randomUUID();
        Booth booth = Booth.builder()
                .id(boothId)
                .name("Booth")
                .company(company)
                .backgroundMusicUrl("https://cdn.example/music.mp3")
                .backgroundMusicPublicId("music_id")
                .backgroundMusicFileName("music.mp3")
                .backgroundMusicFileSize(100L)
                .build();

        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(boothId, company.getId())).thenReturn(Optional.of(booth));
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothResponseDTO response = exhibitorBoothService.deleteBackgroundMusic(exhibitorUser, boothId);

        assertNull(response.getBackgroundMusicUrl());
        assertNull(response.getBackgroundMusicFileName());
        assertNull(response.getBackgroundMusicFileSize());
        assertNull(booth.getBackgroundMusicPublicId());
        verify(assetCleanupService).scheduleCleanup("music_id", "video");
    }
}
