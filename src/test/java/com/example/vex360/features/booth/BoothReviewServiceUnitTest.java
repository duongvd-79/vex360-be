package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.booth.dtos.request.RejectBoothReviewRequest;
import com.example.vex360.features.booth.dtos.response.BoothResponseDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothContentOverviewDTO;
import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.BoothReviewRequest;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.booth.enums.BoothReviewComparisonCompleteness;
import com.example.vex360.features.booth.enums.BoothReviewStatus;
import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.booth.mapper.BoothMapper;
import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.BoothReviewRequestRepository;
import com.example.vex360.features.booth.services.BoothReviewDiffService;
import com.example.vex360.features.booth.services.BoothReviewPolicyService;
import com.example.vex360.features.booth.services.BoothReviewService;
import com.example.vex360.features.booth.services.BoothReviewSnapshotFactory;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BoothReviewServiceUnitTest {
    @Mock BoothRepository boothRepository;
    @Mock BoothReviewRequestRepository reviewRepository;
    @Mock CompanyService companyService;
    @Mock BoothReviewPolicyService policyService;

    private BoothReviewService service;
    private User exhibitor;
    private User organizer;
    private Company company;
    private Booth booth;
    private UUID exhibitionUuid;

    @BeforeEach
    void setup() {
        service = new BoothReviewService(
                boothRepository,
                reviewRepository,
                companyService,
                Mappers.getMapper(BoothMapper.class),
                policyService,
                new BoothReviewSnapshotFactory(),
                new BoothReviewDiffService());
        exhibitor = User.builder().id(UUID.randomUUID()).fullName("Exhibitor Owner").build();
        organizer = User.builder().id(UUID.randomUUID()).build();
        company = Company.builder().id(UUID.randomUUID()).name("VEX").ownerUser(exhibitor)
                .email("contact@vex.com").phone("0901234567").build();
        exhibitionUuid = UUID.randomUUID();
        booth = booth(BoothStatus.DRAFT);
    }

    @Test
    void submitReviewAllocatesVersionAndStoresSchema2Snapshot() {
        when(companyService.getCompanyEntityForCurrentUser(exhibitor)).thenReturn(company);
        when(boothRepository.findCompanyBoothByIdForUpdate(booth.getId(), company.getId()))
                .thenReturn(Optional.of(booth));
        when(reviewRepository.findTopByBoothIdOrderByVersionNumberDescSubmittedAtDesc(booth.getId()))
                .thenReturn(Optional.empty());
        when(reviewRepository.countByBoothId(booth.getId())).thenReturn(2L);
        when(reviewRepository.save(any(BoothReviewRequest.class))).thenAnswer(invocation -> {
            BoothReviewRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });
        when(boothRepository.save(booth)).thenReturn(booth);

        BoothReviewRequestSummaryDTO response = service.submitReview(exhibitor, booth.getId());

        assertEquals(3, response.getVersionNumber());
        assertEquals(BoothReviewStatus.PENDING, response.getStatus());
        assertEquals(BoothStatus.PENDING, booth.getStatus());
        assertTrue(response.getChangeSummary().isInitialSubmission());
        assertEquals(BoothReviewComparisonCompleteness.UNAVAILABLE,
                response.getChangeSummary().getComparisonCompleteness());
        assertEquals(3, response.getChangeSummary().getVersionNumber());
        verify(boothRepository).findCompanyBoothByIdForUpdate(booth.getId(), company.getId());
    }

    @Test
    void contentOverviewSupportsPendingPublishedAndArchived() {
        addContentTree();
        BoothReviewRequest pending = reviewRequest(BoothReviewStatus.PENDING, 4);
        when(boothRepository.findDetailForOrganizer(booth.getId(), exhibitionUuid, organizer.getId()))
                .thenReturn(Optional.of(booth));
        when(reviewRepository.findTopByBoothIdAndStatusOrderBySubmittedAtDesc(
                booth.getId(), BoothReviewStatus.PENDING)).thenReturn(Optional.of(pending));

        booth.setStatus(BoothStatus.PENDING);
        OrganizerBoothContentOverviewDTO pendingResult =
                service.getContentOverviewForOrganizer(organizer, exhibitionUuid, booth.getId());
        assertTrue(pendingResult.getReviewContext().isCanApprove());
        assertTrue(pendingResult.getReviewContext().isCanReject());
        assertEquals(pending.getId(), pendingResult.getReviewContext().getPendingReviewRequestId());
        assertEquals(1, pendingResult.getContentOverview().getPanoramaCount());
        assertEquals(2, pendingResult.getContentOverview().getHotspotCount());
        assertEquals(1, pendingResult.getContentOverview().getProductCount());
        assertEquals(1, pendingResult.getContentOverview().getProductContentCount());
        assertEquals(1, pendingResult.getContentOverview().getMediaAssetCount());
        assertEquals(2, pendingResult.getContentOverview().getHotspots().size());
        assertEquals(1, pendingResult.getContentOverview().getProducts().get(0).getContents().size());
        assertEquals("Exhibitor Owner", pendingResult.getBooth().getOwnerName());
        assertEquals("Premium", pendingResult.getBooth().getPackageName());
        assertEquals("contact@vex.com", pendingResult.getBooth().getContactEmail());
        assertEquals("0901234567", pendingResult.getBooth().getContactPhone());
        assertEquals("music.mp3", pendingResult.getBooth().getBackgroundMusicFileName());
        assertEquals(1024L, pendingResult.getBooth().getBackgroundMusicFileSize());

        booth.setStatus(BoothStatus.PUBLISHED);
        OrganizerBoothContentOverviewDTO published =
                service.getContentOverviewForOrganizer(organizer, exhibitionUuid, booth.getId());
        assertFalse(published.getReviewContext().isCanApprove());
        assertEquals(null, published.getReviewContext().getPendingReviewRequestId());

        booth.setStatus(BoothStatus.ARCHIVED);
        OrganizerBoothContentOverviewDTO archived =
                service.getContentOverviewForOrganizer(organizer, exhibitionUuid, booth.getId());
        assertFalse(archived.getReviewContext().isCanReject());
    }

    @Test
    void pendingBoothWithoutPendingRequestIsInconsistent() {
        booth.setStatus(BoothStatus.PENDING);
        when(boothRepository.findDetailForOrganizer(booth.getId(), exhibitionUuid, organizer.getId()))
                .thenReturn(Optional.of(booth));
        when(reviewRepository.findTopByBoothIdAndStatusOrderBySubmittedAtDesc(
                booth.getId(), BoothReviewStatus.PENDING)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> service.getContentOverviewForOrganizer(organizer, exhibitionUuid, booth.getId()));
        assertSame(ErrorCode.BOOTH_REVIEW_REQUEST_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void tourPreviewSupportsAllReviewVisibleStatuses() {
        addContentTree();
        when(boothRepository.findDetailForOrganizer(booth.getId(), exhibitionUuid, organizer.getId()))
                .thenReturn(Optional.of(booth));
        for (BoothStatus status : List.of(BoothStatus.PENDING, BoothStatus.PUBLISHED, BoothStatus.ARCHIVED)) {
            booth.setStatus(status);
            BoothResponseDTO result = service.getTourPreviewForOrganizer(organizer, exhibitionUuid, booth.getId());
            assertEquals(status, result.getStatus());
            assertEquals(1, result.getPanoramas().size());
            assertEquals(2, result.getPanoramas().get(0).getHotspots().size());
        }
    }

    @Test
    void organizerCannotReadBoothOutsideOwnedExhibition() {
        when(boothRepository.findDetailForOrganizer(booth.getId(), exhibitionUuid, organizer.getId()))
                .thenReturn(Optional.empty());
        AppException exception = assertThrows(AppException.class,
                () -> service.getTourPreviewForOrganizer(organizer, exhibitionUuid, booth.getId()));
        assertSame(ErrorCode.BOOTH_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void approveAndRejectReturnSummaryOnly() {
        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest approveRequest = reviewRequest(BoothReviewStatus.PENDING, 1);
        when(policyService.getOrganizerReviewRequest(organizer, exhibitionUuid, approveRequest.getId()))
                .thenReturn(approveRequest);
        when(reviewRepository.save(approveRequest)).thenReturn(approveRequest);
        when(boothRepository.save(booth)).thenReturn(booth);
        BoothReviewRequestSummaryDTO approved = service.approve(organizer, exhibitionUuid, approveRequest.getId());
        assertEquals(BoothReviewStatus.APPROVED, approved.getStatus());
        assertEquals(BoothStatus.PUBLISHED, booth.getStatus());

        booth.setStatus(BoothStatus.PENDING);
        BoothReviewRequest rejectRequest = reviewRequest(BoothReviewStatus.PENDING, 2);
        when(policyService.getOrganizerReviewRequest(organizer, exhibitionUuid, rejectRequest.getId()))
                .thenReturn(rejectRequest);
        when(reviewRepository.save(rejectRequest)).thenReturn(rejectRequest);
        BoothReviewRequestSummaryDTO rejected = service.reject(organizer, exhibitionUuid, rejectRequest.getId(),
                new RejectBoothReviewRequest("Missing content"));
        assertEquals("Missing content", rejected.getRejectedReason());
        assertEquals(BoothStatus.DRAFT, booth.getStatus());
    }

    @Test
    void approveAndRejectRequireBoothToBePending() {
        booth.setStatus(BoothStatus.PUBLISHED);
        BoothReviewRequest approveRequest = reviewRequest(BoothReviewStatus.PENDING, 1);
        when(policyService.getOrganizerReviewRequest(organizer, exhibitionUuid, approveRequest.getId()))
                .thenReturn(approveRequest);

        AppException approveException = assertThrows(AppException.class,
                () -> service.approve(organizer, exhibitionUuid, approveRequest.getId()));
        assertSame(ErrorCode.INVALID_BOOTH_REVIEW_STATUS, approveException.getErrorCode());

        BoothReviewRequest rejectRequest = reviewRequest(BoothReviewStatus.PENDING, 2);
        when(policyService.getOrganizerReviewRequest(organizer, exhibitionUuid, rejectRequest.getId()))
                .thenReturn(rejectRequest);
        AppException rejectException = assertThrows(AppException.class,
                () -> service.reject(organizer, exhibitionUuid, rejectRequest.getId(),
                        new RejectBoothReviewRequest("Reason")));
        assertSame(ErrorCode.INVALID_BOOTH_REVIEW_STATUS, rejectException.getErrorCode());
    }

    @Test
    void organizerHistoryRejectsDraftBooth() {
        booth.setStatus(BoothStatus.DRAFT);
        when(boothRepository.findDetailForOrganizer(booth.getId(), exhibitionUuid, organizer.getId()))
                .thenReturn(Optional.of(booth));

        AppException exception = assertThrows(AppException.class,
                () -> service.getReviewHistoryForOrganizer(
                        organizer,
                        exhibitionUuid,
                        booth.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)));

        assertSame(ErrorCode.INVALID_BOOTH_REVIEW_STATUS, exception.getErrorCode());
    }

    private void addContentTree() {
        ProductContent content = ProductContent.builder().id(UUID.randomUUID()).type(ProductContentType.IMAGE)
                .contentUrl("content.jpg").mimeType("image/jpeg").fileSize(10L).orderIndex(0).build();
        Product product = Product.builder().id(UUID.randomUUID()).company(company).name("Product").sku("SKU")
                .description("Description").thumbnailUrl("thumb.jpg").price(BigDecimal.ONE).currency("VND")
                .status(ProductStatus.ACTIVE).contents(List.of(content)).build();
        content.setProduct(product);
        MediaAsset media = MediaAsset.builder().id(UUID.randomUUID()).company(company).name("Media")
                .type(MediaAssetType.IMAGE).url("media.jpg").mimeType("image/jpeg").fileSize(20L).build();
        Panorama panorama = Panorama.builder().id(UUID.randomUUID()).booth(booth).name("Entrance")
                .imageUrl("pano.jpg").orderIndex(0).isDefault(true).build();
        Hotspot productHotspot = hotspot(panorama, "Product hotspot", HotspotType.PRODUCT);
        productHotspot.setProduct(product);
        Hotspot mediaHotspot = hotspot(panorama, "Media hotspot", HotspotType.MEDIA);
        mediaHotspot.setMediaAsset(media);
        panorama.setHotspots(List.of(productHotspot, mediaHotspot));
        booth.setPanoramas(List.of(panorama));
    }

    private Hotspot hotspot(Panorama panorama, String name, HotspotType type) {
        return Hotspot.builder().id(UUID.randomUUID()).sourcePanorama(panorama).name(name).type(type)
                .xPosition(1.0).yPosition(2.0).zPosition(3.0).build();
    }

    private BoothReviewRequest reviewRequest(BoothReviewStatus status, int version) {
        return BoothReviewRequest.builder().id(UUID.randomUUID()).booth(booth).status(status)
                .submittedBy(exhibitor).versionNumber(version).build();
    }

    private Booth booth(BoothStatus status) {
        Exhibition exhibition = Exhibition.builder().uuid(exhibitionUuid).name("Expo")
                .organizer(organizer).startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder().exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .exhibitionPackage(exhibitionPackage).packageNameSnapshot("Premium").build();
        return Booth.builder().id(UUID.randomUUID()).name("Booth").company(company).status(status)
                .isTemplate(false).exhibitorRegistration(registration)
                .backgroundMusicFileName("music.mp3").backgroundMusicFileSize(1024L).build();
    }
}
