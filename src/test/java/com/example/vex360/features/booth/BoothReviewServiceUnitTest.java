package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothReviewContentOverviewDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewMediaItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewProductItemDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestDetailDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothReviewServiceUnitTest {
    @Mock
    private BoothRepository boothRepository;

    @Mock
    private BoothReviewRequestRepository boothReviewRequestRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private BoothReviewPolicyService boothReviewPolicyService;

    private BoothReviewService boothReviewService;
    private User exhibitorUser;
    private User organizer;
    private Company company;
    private Booth booth;
    private UUID exhibitionUuid;

    @BeforeEach
    void setup() {
        boothReviewService = new BoothReviewService(
                boothRepository,
                boothReviewRequestRepository,
                companyService,
                Mappers.getMapper(BoothMapper.class),
                boothReviewPolicyService);
        exhibitorUser = User.builder().id(UUID.randomUUID()).email("exhibitor@example.com").build();
        organizer = User.builder().id(UUID.randomUUID()).email("organizer@example.com").build();
        company = Company.builder().id(UUID.randomUUID()).ownerUser(exhibitorUser).name("VEX Company").build();
        exhibitionUuid = UUID.randomUUID();
        booth = booth(BoothStatus.DRAFT);
    }

    @Test
    void submitReviewCreatesPendingRequestAndMarksBoothPending() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitorUser)).thenReturn(company);
        when(boothRepository.findCompanyBoothById(booth.getId(), company.getId())).thenReturn(Optional.of(booth));
        when(boothReviewRequestRepository.save(org.mockito.ArgumentMatchers.any(BoothReviewRequest.class)))
                .thenAnswer(invocation -> {
                    BoothReviewRequest request = invocation.getArgument(0);
                    request.setId(UUID.randomUUID());
                    return request;
                });
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.submitReview(exhibitorUser, booth.getId());

        assertNotNull(response.getRequest().getId());
        assertEquals(BoothReviewStatus.PENDING, response.getRequest().getStatus());
        assertEquals(BoothStatus.PENDING, booth.getStatus());
        assertNotNull(response.getContentOverview());
        assertEquals(0, response.getContentOverview().getPanoramaCount());
        assertEquals(0, response.getContentOverview().getHotspotCount());
        assertEquals(0, response.getContentOverview().getProductCount());
        assertEquals(0, response.getContentOverview().getMediaAssetCount());
        assertEquals(0, response.getContentOverview().getPanoramas().size());
        assertEquals(0, response.getContentOverview().getProducts().size());
        assertEquals(0, response.getContentOverview().getMediaAssets().size());
    }

    @Test
    void approvePendingRequestPublishesBooth() {
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.PENDING);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);
        when(boothReviewRequestRepository.save(request)).thenReturn(request);
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.approve(organizer, exhibitionUuid, request.getId());

        assertEquals(BoothReviewStatus.APPROVED, response.getRequest().getStatus());
        assertEquals(BoothStatus.PUBLISHED, response.getBooth().getStatus());
    }

    @Test
    void rejectPendingRequestStoresReasonAndReturnsBoothToDraft() {
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.PENDING);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);
        when(boothReviewRequestRepository.save(request)).thenReturn(request);
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestDetailDTO response = boothReviewService.reject(
                organizer,
                exhibitionUuid,
                request.getId(),
                new RejectBoothReviewRequest("Missing default panorama"));

        assertEquals(BoothReviewStatus.REJECTED, response.getRequest().getStatus());
        assertEquals("Missing default panorama", response.getRequest().getRejectedReason());
        assertEquals(BoothStatus.DRAFT, response.getBooth().getStatus());
    }

    @Test
    void approveRejectedRequestThrowsInvalidStatus() {
        BoothReviewRequest request = reviewRequest(BoothReviewStatus.REJECTED);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);

        AppException exception = assertThrows(
                AppException.class,
                () -> boothReviewService.approve(organizer, exhibitionUuid, request.getId()));

        assertSame(ErrorCode.INVALID_BOOTH_REVIEW_STATUS, exception.getErrorCode());
    }

    @Test
    void getRequestForOrganizerIncludesContentOverview() {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("VR Headset")
                .sku("VR-001")
                .description("Lightweight headset")
                .thumbnailUrl("https://cdn.example.com/vr.png")
                .price(BigDecimal.valueOf(1200000))
                .currency("VND")
                .status(ProductStatus.ACTIVE)
                .build();
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .name("Intro Video")
                .type(MediaAssetType.VIDEO)
                .url("https://cdn.example.com/intro.mp4")
                .publicId("media/intro")
                .mimeType("video/mp4")
                .fileSize(2048L)
                .build();

        Panorama entrance = panorama("Entrance", 0, true);
        Panorama showcase = panorama("Showcase", 1, false);
        Hotspot productHotspotOne = hotspot("Product A", entrance, HotspotType.PRODUCT, product, null);
        Hotspot mediaHotspotOne = hotspot("Media A", entrance, HotspotType.MEDIA, null, mediaAsset);
        Hotspot productHotspotTwo = hotspot("Product B", showcase, HotspotType.PRODUCT, product, null);
        Hotspot mediaHotspotTwo = hotspot("Media B", showcase, HotspotType.MEDIA, null, mediaAsset);
        entrance.setHotspots(List.of(productHotspotOne, mediaHotspotOne));
        showcase.setHotspots(List.of(productHotspotTwo, mediaHotspotTwo));
        booth.setPanoramas(List.of(showcase, entrance));

        BoothReviewRequest request = reviewRequest(BoothReviewStatus.PENDING);
        when(boothReviewPolicyService.getOrganizerReviewRequest(organizer, exhibitionUuid, request.getId()))
                .thenReturn(request);

        BoothReviewRequestDetailDTO response = boothReviewService.getRequestForOrganizer(
                organizer,
                exhibitionUuid,
                request.getId());

        BoothReviewContentOverviewDTO overview = response.getContentOverview();
        assertNotNull(overview);
        assertEquals(2, overview.getPanoramaCount());
        assertEquals(4, overview.getHotspotCount());
        assertEquals(1, overview.getProductCount());
        assertEquals(1, overview.getMediaAssetCount());
        assertEquals(entrance.getId(), overview.getPanoramas().get(0).getId());
        assertEquals(2, overview.getPanoramas().get(0).getHotspotCount());
        assertEquals(showcase.getId(), overview.getPanoramas().get(1).getId());
        assertEquals(2, overview.getPanoramas().get(1).getHotspotCount());

        BoothReviewProductItemDTO productItem = overview.getProducts().get(0);
        assertEquals(product.getId(), productItem.getId());
        assertEquals("VR Headset", productItem.getName());
        assertEquals("VR-001", productItem.getSku());
        assertEquals("Lightweight headset", productItem.getDescription());
        assertEquals("https://cdn.example.com/vr.png", productItem.getThumbnailUrl());
        assertEquals(BigDecimal.valueOf(1200000), productItem.getPrice());
        assertEquals("VND", productItem.getCurrency());
        assertEquals(ProductStatus.ACTIVE, productItem.getStatus());
        assertEquals(2, productItem.getUsageCount());
        assertEquals(2, productItem.getPlacements().size());
        assertEquals(entrance.getId(), productItem.getPlacements().get(0).getPanoramaId());
        assertEquals(productHotspotOne.getId(), productItem.getPlacements().get(0).getHotspotId());

        BoothReviewMediaItemDTO mediaItem = overview.getMediaAssets().get(0);
        assertEquals(mediaAsset.getId(), mediaItem.getId());
        assertEquals("Intro Video", mediaItem.getName());
        assertEquals(MediaAssetType.VIDEO, mediaItem.getType());
        assertEquals("https://cdn.example.com/intro.mp4", mediaItem.getUrl());
        assertEquals("video/mp4", mediaItem.getMimeType());
        assertEquals(2048L, mediaItem.getFileSize());
        assertEquals(2, mediaItem.getUsageCount());
        assertEquals(2, mediaItem.getPlacements().size());
        assertEquals(entrance.getId(), mediaItem.getPlacements().get(0).getPanoramaId());
        assertEquals(mediaHotspotOne.getId(), mediaItem.getPlacements().get(0).getHotspotId());
    }

    private BoothReviewRequest reviewRequest(BoothReviewStatus status) {
        return BoothReviewRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(status)
                .submittedBy(exhibitorUser)
                .build();
    }

    private Booth booth(BoothStatus status) {
        Exhibition exhibition = Exhibition.builder()
                .id(1)
                .uuid(exhibitionUuid)
                .name("Expo")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(12))
                .organizer(organizer)
                .build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder()
                .id(1)
                .exhibition(exhibition)
                .build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .id(1)
                .exhibitionPackage(exhibitionPackage)
                .build();
        return Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth")
                .company(company)
                .status(status)
                .isTemplate(false)
                .exhibitorRegistration(registration)
                .build();
    }

    private Panorama panorama(String name, int orderIndex, boolean isDefault) {
        return Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name(name)
                .imageUrl("https://cdn.example.com/" + name + ".jpg")
                .imageKey("panoramas/" + name)
                .orderIndex(orderIndex)
                .isDefault(isDefault)
                .build();
    }

    private Hotspot hotspot(
            String name,
            Panorama sourcePanorama,
            HotspotType type,
            Product product,
            MediaAsset mediaAsset) {
        return Hotspot.builder()
                .id(UUID.randomUUID())
                .name(name)
                .sourcePanorama(sourcePanorama)
                .type(type)
                .product(product)
                .mediaAsset(mediaAsset)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
    }
}
