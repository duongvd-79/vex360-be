package com.example.vex360.features.assetcleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService;
import com.example.vex360.features.assetcleanup.services.CloudinaryAssetInventoryService.AssetPage;

@ExtendWith(MockitoExtension.class)
class CloudinaryAssetInventoryServiceUnitTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Api api;

    @Mock
    private ApiResponse response;

    @Test
    void listsOwnedPrefixWithCursorAndParsesAssets() throws Exception {
        when(cloudinary.api()).thenReturn(api);
        when(response.get("resources")).thenReturn(List.of(Map.of(
                "public_id", "image/2026-08-01/orphan",
                "resource_type", "image",
                "created_at", "2026-08-01T00:00:00Z")));
        when(response.get("next_cursor")).thenReturn("next");
        when(api.resources(argThat(options -> "image/".equals(options.get("prefix"))
                && "image".equals(options.get("resource_type"))
                && "cursor".equals(options.get("next_cursor"))
                && Integer.valueOf(100).equals(options.get("max_results")))))
                .thenReturn(response);

        AssetPage page = new CloudinaryAssetInventoryService(cloudinary)
                .list("image/", "image", "cursor", 100);

        assertEquals("next", page.nextCursor());
        assertEquals("image/2026-08-01/orphan", page.assets().getFirst().publicId());
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), page.assets().getFirst().createdAt());
    }
}
