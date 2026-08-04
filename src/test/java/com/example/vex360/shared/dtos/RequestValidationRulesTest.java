package com.example.vex360.shared.dtos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.analytics.dtos.request.RecordAnalyticsEventRequest;
import com.example.vex360.features.analytics.enums.AnalyticsEventType;
import com.example.vex360.features.chat.dtos.SendChatMessageRequest;
import com.example.vex360.features.designrequest.dtos.request.ReorderDesignDraftPanoramasRequest;
import com.example.vex360.features.product.dtos.request.UpdateProductRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RequestValidationRulesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsPartialThumbnailMetadata() {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setThumbnailUrl("https://cdn.example.com/product.png");

        assertHasMessage(request,
                "URL, Public ID và kích thước ảnh đại diện phải được cung cấp cùng nhau.");
    }

    @Test
    void requiresProductForProductClickEvent() {
        RecordAnalyticsEventRequest request = new RecordAnalyticsEventRequest();
        request.setEventType(AnalyticsEventType.PRODUCT_CLICK);
        request.setBoothId(UUID.randomUUID());

        assertHasMessage(request, "Sản phẩm không được để trống với sự kiện PRODUCT_CLICK.");
    }

    @Test
    void rejectsDuplicatePanoramaIds() {
        UUID panoramaId = UUID.randomUUID();
        ReorderDesignDraftPanoramasRequest request =
                new ReorderDesignDraftPanoramasRequest(List.of(panoramaId, panoramaId));

        assertHasMessage(request, "Danh sách panorama không được chứa ID trùng lặp.");
    }

    @Test
    void rejectsBlankChatMessage() {
        SendChatMessageRequest request = new SendChatMessageRequest(UUID.randomUUID(), " ");

        assertHasMessage(request, "Nội dung tin nhắn không được để trống.");
    }

    @Test
    void rejectsUnsupportedCloudinaryResourceType() {
        DeleteUploadRequest request = new DeleteUploadRequest("public-id", "raw");

        assertHasMessage(request, "Loại tài nguyên chỉ được là image hoặc video.");
    }

    @Test
    void rejectsCloudinaryPublicIdLongerThanFiveHundredCharacters() {
        DeleteUploadRequest request = new DeleteUploadRequest("a".repeat(501), "image");

        assertHasMessage(request, "Public ID không được vượt quá 500 ký tự.");
    }

    private void assertHasMessage(Object request, String expectedMessage) {
        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getMessage().equals(expectedMessage)));
    }
}
