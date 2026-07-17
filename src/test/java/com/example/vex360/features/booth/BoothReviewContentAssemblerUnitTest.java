package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.example.vex360.features.booth.dtos.response.BoothReviewChangeSummaryDTO;
import com.example.vex360.features.booth.dtos.response.BoothReviewRequestSummaryDTO;
import com.example.vex360.features.booth.dtos.response.OrganizerBoothContentOverviewDTO;
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
import com.example.vex360.features.booth.services.BoothReviewContentAssembler;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.entities.ProductContent;
import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.user.entities.User;

class BoothReviewContentAssemblerUnitTest {
    private BoothReviewContentAssembler assembler;
    private Booth booth;

    @BeforeEach
    void setup() {
        assembler = new BoothReviewContentAssembler(Mappers.getMapper(BoothMapper.class));
        User owner = User.builder().id(UUID.randomUUID()).fullName("Exhibitor Owner").build();
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .name("VEX")
                .ownerUser(owner)
                .email("contact@vex.com")
                .phone("0901234567")
                .build();
        Exhibition exhibition = Exhibition.builder().uuid(UUID.randomUUID()).name("Expo").build();
        ExhibitionPackage exhibitionPackage = ExhibitionPackage.builder().exhibition(exhibition).build();
        ExhibitorRegistration registration = ExhibitorRegistration.builder()
                .exhibitionPackage(exhibitionPackage)
                .packageNameSnapshot("Premium")
                .build();
        booth = Booth.builder()
                .id(UUID.randomUUID())
                .name("Booth")
                .company(company)
                .status(BoothStatus.PENDING)
                .exhibitorRegistration(registration)
                .backgroundMusicFileName("music.mp3")
                .backgroundMusicFileSize(1024L)
                .build();
        addContentTree(company);
    }

    @Test
    void organizerOverviewSortsAndDeduplicatesContentWithPlacements() {
        BoothReviewRequest pending = BoothReviewRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(BoothReviewStatus.PENDING)
                .versionNumber(4)
                .build();

        OrganizerBoothContentOverviewDTO result = assembler.toOrganizerContentOverview(booth, pending);

        assertTrue(result.getReviewContext().isCanApprove());
        assertTrue(result.getReviewContext().isCanReject());
        assertEquals(pending.getId(), result.getReviewContext().getPendingReviewRequestId());
        assertEquals(2, result.getContentOverview().getPanoramaCount());
        assertEquals("First", result.getContentOverview().getPanoramas().get(0).getName());
        assertEquals(3, result.getContentOverview().getHotspotCount());
        assertEquals(1, result.getContentOverview().getProductCount());
        assertEquals(2, result.getContentOverview().getProductContentCount());
        assertEquals(1, result.getContentOverview().getMediaAssetCount());
        assertEquals(2, result.getContentOverview().getProducts().get(0).getUsageCount());
        assertEquals(2, result.getContentOverview().getProducts().get(0).getPlacements().size());
        assertEquals(0, result.getContentOverview().getProducts().get(0).getContents().get(0).getOrderIndex());
        assertEquals("Exhibitor Owner", result.getBooth().getOwnerName());
        assertEquals("Premium", result.getBooth().getPackageName());
    }

    @Test
    void requestSummaryUsesProvidedChangeSummary() {
        BoothReviewRequest request = BoothReviewRequest.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .status(BoothReviewStatus.APPROVED)
                .versionNumber(2)
                .build();
        BoothReviewChangeSummaryDTO changeSummary = BoothReviewChangeSummaryDTO.builder()
                .versionNumber(2)
                .build();

        BoothReviewRequestSummaryDTO result = assembler.toRequestSummary(request, changeSummary);

        assertSame(changeSummary, result.getChangeSummary());
        assertEquals(booth.getId(), result.getBoothId());
        assertEquals(booth.getCompany().getId(), result.getCompanyId());
        assertEquals(booth.getExhibitorRegistration().getExhibitionPackage().getExhibition().getUuid(),
                result.getExhibitionUuid());
    }

    private void addContentTree(Company company) {
        ProductContent second = ProductContent.builder().id(UUID.randomUUID()).type(ProductContentType.VIDEO)
                .contentUrl("second.mp4").mimeType("video/mp4").fileSize(20L).orderIndex(1).build();
        ProductContent first = ProductContent.builder().id(UUID.randomUUID()).type(ProductContentType.IMAGE)
                .contentUrl("first.jpg").mimeType("image/jpeg").fileSize(10L).orderIndex(0).build();
        Product product = Product.builder().id(UUID.randomUUID()).company(company).name("Product").sku("SKU")
                .description("Description").thumbnailUrl("thumb.jpg").price(BigDecimal.ONE).currency("VND")
                .status(ProductStatus.ACTIVE).contents(List.of(second, first)).build();
        first.setProduct(product);
        second.setProduct(product);
        MediaAsset media = MediaAsset.builder().id(UUID.randomUUID()).company(company).name("Media")
                .type(MediaAssetType.IMAGE).url("media.jpg").mimeType("image/jpeg").fileSize(20L).build();

        Panorama secondPanorama = panorama("Second", 1);
        Hotspot secondProduct = hotspot(secondPanorama, "Product 2", HotspotType.PRODUCT);
        secondProduct.setProduct(product);
        secondPanorama.setHotspots(List.of(secondProduct));

        Panorama firstPanorama = panorama("First", 0);
        Hotspot firstProduct = hotspot(firstPanorama, "Product 1", HotspotType.PRODUCT);
        firstProduct.setProduct(product);
        Hotspot mediaHotspot = hotspot(firstPanorama, "Media", HotspotType.MEDIA);
        mediaHotspot.setMediaAsset(media);
        firstPanorama.setHotspots(List.of(firstProduct, mediaHotspot));
        booth.setPanoramas(List.of(secondPanorama, firstPanorama));
    }

    private Panorama panorama(String name, int orderIndex) {
        return Panorama.builder()
                .id(UUID.randomUUID())
                .booth(booth)
                .name(name)
                .imageUrl(name + ".jpg")
                .orderIndex(orderIndex)
                .isDefault(orderIndex == 0)
                .build();
    }

    private Hotspot hotspot(Panorama panorama, String name, HotspotType type) {
        return Hotspot.builder()
                .id(UUID.randomUUID())
                .sourcePanorama(panorama)
                .name(name)
                .type(type)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
    }
}
