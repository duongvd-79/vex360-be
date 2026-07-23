package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.vex360.features.booth.dtos.request.CreateMediaAssetRequest;
import com.example.vex360.features.booth.dtos.response.MediaAssetResponseDTO;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.CompanyStorageService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.dtos.CloudinaryResponse;
import com.example.vex360.shared.dtos.PageResponse;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import com.example.vex360.shared.services.CloudService;

@ExtendWith(MockitoExtension.class)
class ExhibitorMediaAssetServiceUnitTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private CloudService cloudService;

    @Mock
    private CompanyStorageService companyStorageService;

    private ExhibitorMediaAssetService mediaAssetService;

    private User currentUser;
    private Company company;
    private MediaAsset mediaAsset;

    @BeforeEach
    void setUp() {
        BoothMapper boothMapper = Mappers.getMapper(BoothMapper.class);
        mediaAssetService = new ExhibitorMediaAssetService(
                mediaAssetRepository,
                hotspotRepository,
                companyService,
                companyStorageService,
                cloudService,
                boothMapper
        );

        currentUser = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).name("Company Corp").build();
        mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Logo")
                .type(MediaAssetType.IMAGE)
                .url("http://image.url")
                .publicId("public-123")
                .mimeType("image/png")
                .fileSize(1024L)
                .build();
    }

    @Test
    void testGetMediaAssets_Success() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        Pageable pageable = PageRequest.of(0, 10);
        Page<MediaAsset> page = new PageImpl<>(List.of(mediaAsset));
        when(mediaAssetRepository.findByCompanyId(company.getId(), pageable)).thenReturn(page);

        PageResponse<MediaAssetResponseDTO> response = mediaAssetService.getMediaAssets(currentUser, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Logo", response.getContent().get(0).getName());
    }

    @Test
    void testCreateMediaAsset_NullRequest_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "data".getBytes());

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, null, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_NullName_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "data".getBytes());
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_BlankName_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "data".getBytes());
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("   ");

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_NullFile_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Logo");

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, null);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_EmptyFile_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Logo");
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_InvalidMimeType_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Doc");
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }

    @Test
    void testCreateMediaAsset_Success_Image() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Image Name");
        MultipartFile file = new MockMultipartFile("file", "image.jpg", "image/jpeg", "data".getBytes());

        CloudinaryResponse uploadResponse = CloudinaryResponse.builder()
                .url("http://cloud/image.jpg")
                .publicId("pub-1")
                .fileType("image/jpeg")
                .fileSize(100L)
                .build();
        when(cloudService.upload(file)).thenReturn(uploadResponse);

        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAssetResponseDTO result = mediaAssetService.createMediaAsset(currentUser, request, file);

        assertNotNull(result);
        assertEquals("Image Name", result.getName());
        assertEquals("http://cloud/image.jpg", result.getUrl());
        assertEquals("image/jpeg", result.getMimeType());
        assertEquals(100L, result.getFileSize());
        assertEquals(MediaAssetType.IMAGE, result.getType());
        verify(mediaAssetRepository).save(any(MediaAsset.class));
    }

    @Test
    void testCreateMediaAsset_Success_Video_NullFileTypeAndSizeFallback() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Video Name");
        MultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "video-data".getBytes());

        CloudinaryResponse uploadResponse = CloudinaryResponse.builder()
                .url("http://cloud/video.mp4")
                .publicId("pub-2")
                .fileType(null)
                .fileSize(null)
                .build();
        when(cloudService.upload(file)).thenReturn(uploadResponse);

        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAssetResponseDTO result = mediaAssetService.createMediaAsset(currentUser, request, file);

        assertNotNull(result);
        assertEquals("Video Name", result.getName());
        assertEquals("video/mp4", result.getMimeType());
        assertEquals(10L, result.getFileSize()); // fallback to file size
        assertEquals(MediaAssetType.VIDEO, result.getType());
    }

    @Test
    void testDeleteMediaAsset_NotFound_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findByIdAndCompanyId(assetId, company.getId())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.deleteMediaAsset(currentUser, assetId);
        });
        assertEquals(ErrorCode.MEDIA_ASSET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testDeleteMediaAsset_UsedInHotspot_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        UUID assetId = mediaAsset.getId();
        when(mediaAssetRepository.findByIdAndCompanyId(assetId, company.getId())).thenReturn(Optional.of(mediaAsset));
        when(hotspotRepository.existsByMediaAssetId(assetId)).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.deleteMediaAsset(currentUser, assetId);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
        verify(mediaAssetRepository, never()).delete(any());
    }

    @Test
    void testDeleteMediaAsset_Success_Image() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        UUID assetId = mediaAsset.getId();
        when(mediaAssetRepository.findByIdAndCompanyId(assetId, company.getId())).thenReturn(Optional.of(mediaAsset));
        when(hotspotRepository.existsByMediaAssetId(assetId)).thenReturn(false);

        MediaAssetResponseDTO response = mediaAssetService.deleteMediaAsset(currentUser, assetId);

        assertNotNull(response);
        assertEquals(assetId, response.getId());
        verify(mediaAssetRepository).delete(mediaAsset);
        verify(cloudService).delete("public-123", "image");
    }

    @Test
    void testDeleteMediaAsset_Success_Video() {
        mediaAsset.setType(MediaAssetType.VIDEO);
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        UUID assetId = mediaAsset.getId();
        when(mediaAssetRepository.findByIdAndCompanyId(assetId, company.getId())).thenReturn(Optional.of(mediaAsset));
        when(hotspotRepository.existsByMediaAssetId(assetId)).thenReturn(false);

        MediaAssetResponseDTO response = mediaAssetService.deleteMediaAsset(currentUser, assetId);

        assertNotNull(response);
        verify(mediaAssetRepository).delete(mediaAsset);
        verify(cloudService).delete("public-123", "video");
    }

    @Test
    void testCreateMediaAsset_NullContentType_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        CreateMediaAssetRequest request = new CreateMediaAssetRequest();
        request.setName("Name");

        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(null);

        AppException exception = assertThrows(AppException.class, () -> {
            mediaAssetService.createMediaAsset(currentUser, request, file);
        });
        assertEquals(ErrorCode.INVALID_MEDIA_ASSET, exception.getErrorCode());
    }
}
