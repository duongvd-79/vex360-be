package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.HotspotInfoContentType;
import com.example.vex360.features.booth.enums.HotspotMediaClickAction;
import com.example.vex360.features.booth.enums.HotspotType;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.designrequest.services.DesignDraftGraphValidator;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

class DesignDraftGraphValidatorUnitTest {
    private DesignDraftGraphValidator validator;
    private Company company;
    private DesignRequest request;
    private Product allowedProduct;

    @BeforeEach
    void setup() {
        validator = new DesignDraftGraphValidator();
        company = Company.builder().id(UUID.randomUUID()).build();
        request = DesignRequest.builder().id(UUID.randomUUID()).company(company).build();
        allowedProduct = Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .status(ProductStatus.ACTIVE)
                .build();
        request.getProducts().add(DesignRequestProduct.builder()
                .designRequest(request)
                .product(allowedProduct)
                .build());
    }

    @Test
    void workingGraphMayBeEmptyButSubmissionMayNot() {
        DesignDraft emptyDraft = DesignDraft.builder().designRequest(request).versionNumber(0).build();

        assertDoesNotThrow(() -> validator.validateWorkingGraph(request, emptyDraft));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_PANORAMA_INVALID,
                () -> validator.validateForSubmission(request, emptyDraft));
    }

    @Test
    void validGraphSupportsAllHotspotContentShapes() {
        DesignDraft draft = validDraft();
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        panorama.getHotspots().add(nav(panorama, "p0"));
        panorama.getHotspots().add(productHotspot(panorama, allowedProduct));
        panorama.getHotspots().add(infoText(panorama, "Welcome"));
        panorama.getHotspots().add(infoMedia(panorama, HotspotInfoContentType.IMAGE, media(MediaAssetType.IMAGE)));
        panorama.getHotspots().add(infoMedia(panorama, HotspotInfoContentType.VIDEO, media(MediaAssetType.VIDEO)));
        panorama.getHotspots().add(infoProduct(panorama, allowedProduct));
        panorama.getHotspots().add(mediaHotspot(panorama, media(MediaAssetType.VIDEO)));

        assertDoesNotThrow(() -> validator.validateForSubmission(request, draft));
    }

    @Test
    void submissionRequiresExactlyOneDefaultAndContiguousUniqueOrder() {
        DesignDraft noDefault = validDraft();
        noDefault.getPanoramas().get(0).setIsDefault(false);
        assertErrorCode(ErrorCode.DESIGN_DRAFT_DEFAULT_PANORAMA_INVALID,
                () -> validator.validateForSubmission(request, noDefault));

        DesignDraft badOrder = validDraft();
        badOrder.getPanoramas().add(panorama(badOrder, "p1", 2, false));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_PANORAMA_ORDER_INVALID,
                () -> validator.validateForSubmission(request, badOrder));
    }

    @Test
    void danglingNavigationTargetIsInvalid() {
        DesignDraft draft = validDraft();
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        panorama.getHotspots().add(nav(panorama, "missing"));

        assertErrorCode(ErrorCode.DESIGN_DRAFT_HOTSPOT_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, draft));
    }

    @Test
    void productMustBeActiveCompanyOwnedAndAllowlisted() {
        DesignDraft draft = validDraft();
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        Product notAllowed = Product.builder()
                .id(UUID.randomUUID())
                .company(company)
                .status(ProductStatus.ACTIVE)
                .build();
        panorama.getHotspots().add(productHotspot(panorama, notAllowed));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_PRODUCT_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, draft));

        panorama.getHotspots().clear();
        allowedProduct.setStatus(ProductStatus.INACTIVE);
        panorama.getHotspots().add(productHotspot(panorama, allowedProduct));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_PRODUCT_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, draft));
    }

    @Test
    void infoMediaMustMatchExpectedTypeAndCompany() {
        DesignDraft wrongTypeDraft = validDraft();
        DesignDraftPanorama wrongTypePanorama = wrongTypeDraft.getPanoramas().get(0);
        wrongTypePanorama.getHotspots().add(
                infoMedia(wrongTypePanorama, HotspotInfoContentType.IMAGE, media(MediaAssetType.VIDEO)));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, wrongTypeDraft));

        DesignDraft wrongCompanyDraft = validDraft();
        DesignDraftPanorama wrongCompanyPanorama = wrongCompanyDraft.getPanoramas().get(0);
        MediaAsset foreignMedia = media(MediaAssetType.IMAGE);
        foreignMedia.setCompany(Company.builder().id(UUID.randomUUID()).build());
        wrongCompanyPanorama.getHotspots().add(
                infoMedia(wrongCompanyPanorama, HotspotInfoContentType.IMAGE, foreignMedia));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, wrongCompanyDraft));
    }

    @Test
    void officialMediaMustBeAllowlisted() {
        DesignDraft draft = validDraft();
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        MediaAsset notAllowed = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .type(MediaAssetType.IMAGE)
                .build();
        panorama.getHotspots().add(infoMedia(panorama, HotspotInfoContentType.IMAGE, notAllowed));

        assertErrorCode(ErrorCode.DESIGN_DRAFT_MEDIA_REFERENCE_INVALID,
                () -> validator.validateForSubmission(request, draft));
    }

    @Test
    void infoTextRequiresTextAndRejectsStaleResourceFields() {
        DesignDraft missingTextDraft = validDraft();
        DesignDraftPanorama missingTextPanorama = missingTextDraft.getPanoramas().get(0);
        missingTextPanorama.getHotspots().add(infoText(missingTextPanorama, " "));
        assertErrorCode(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID,
                () -> validator.validateForSubmission(request, missingTextDraft));

        DesignDraft staleFieldDraft = validDraft();
        DesignDraftPanorama staleFieldPanorama = staleFieldDraft.getPanoramas().get(0);
        DesignDraftHotspot text = infoText(staleFieldPanorama, "Welcome");
        text.setProduct(allowedProduct);
        staleFieldPanorama.getHotspots().add(text);
        assertErrorCode(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID,
                () -> validator.validateForSubmission(request, staleFieldDraft));
    }

    @Test
    void cornersMustBeCompleteAndOnlyBelongToProductOrMedia() {
        DesignDraft draft = validDraft();
        DesignDraftPanorama panorama = draft.getPanoramas().get(0);
        DesignDraftHotspot media = mediaHotspot(panorama, media(MediaAssetType.IMAGE));
        media.setCornerTlX(1.0);
        panorama.getHotspots().add(media);

        assertErrorCode(ErrorCode.DESIGN_DRAFT_HOTSPOT_INVALID, () -> validator.validateForSubmission(request, draft));
    }

    private DesignDraft validDraft() {
        DesignDraft draft = DesignDraft.builder().designRequest(request).versionNumber(0).build();
        draft.getPanoramas().add(panorama(draft, "p0", 0, true));
        return draft;
    }

    private DesignDraftPanorama panorama(DesignDraft draft, String key, int order, boolean isDefault) {
        return DesignDraftPanorama.builder()
                .draft(draft)
                .clientKey(key)
                .name("Panorama " + key)
                .imageUrl("https://cdn.example.com/" + key)
                .imageKey("image-" + key)
                .orderIndex(order)
                .isDefault(isDefault)
                .build();
    }

    private DesignDraftHotspot baseHotspot(DesignDraftPanorama source, HotspotType type, String name) {
        return DesignDraftHotspot.builder()
                .sourcePanorama(source)
                .type(type)
                .name(name)
                .xPosition(1.0)
                .yPosition(2.0)
                .zPosition(3.0)
                .build();
    }

    private DesignDraftHotspot nav(DesignDraftPanorama source, String targetKey) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.NAV, "Navigation");
        hotspot.setTargetDraftPanoramaKey(targetKey);
        return hotspot;
    }

    private DesignDraftHotspot productHotspot(DesignDraftPanorama source, Product product) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.PRODUCT, "Product");
        hotspot.setProduct(product);
        return hotspot;
    }

    private DesignDraftHotspot infoText(DesignDraftPanorama source, String text) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.INFO, "Info");
        hotspot.setInfoContentType(HotspotInfoContentType.TEXT);
        hotspot.setInfoText(text);
        return hotspot;
    }

    private DesignDraftHotspot infoMedia(
            DesignDraftPanorama source,
            HotspotInfoContentType contentType,
            MediaAsset mediaAsset) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.INFO, "Info media");
        hotspot.setInfoContentType(contentType);
        hotspot.setMediaAsset(mediaAsset);
        return hotspot;
    }

    private DesignDraftHotspot infoProduct(DesignDraftPanorama source, Product product) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.INFO, "Info product");
        hotspot.setInfoContentType(HotspotInfoContentType.PRODUCT);
        hotspot.setProduct(product);
        return hotspot;
    }

    private DesignDraftHotspot mediaHotspot(DesignDraftPanorama source, MediaAsset mediaAsset) {
        DesignDraftHotspot hotspot = baseHotspot(source, HotspotType.MEDIA, "Media");
        hotspot.setMediaAsset(mediaAsset);
        hotspot.setMediaClickAction(HotspotMediaClickAction.DEFAULT);
        return hotspot;
    }

    private MediaAsset media(MediaAssetType type) {
        MediaAsset mediaAsset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .company(company)
                .type(type)
                .build();
        request.getMediaAssets().add(DesignRequestMediaAsset.builder()
                .designRequest(request)
                .mediaAsset(mediaAsset)
                .build());
        return mediaAsset;
    }

    private void assertErrorCode(ErrorCode expectedCode, Runnable operation) {
        AppException exception = assertThrows(AppException.class, operation::run);
        assertSame(expectedCode, exception.getErrorCode());
    }
}
