package com.example.vex360.features.assetcleanup.services;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudinaryAssetInventoryService {

    private final Cloudinary cloudinary;

    public AssetPage list(String prefix, String resourceType, String nextCursor, int maxResults) {
        Map<String, Object> options = new HashMap<>();
        options.put("type", "upload");
        options.put("resource_type", resourceType);
        options.put("prefix", prefix);
        options.put("max_results", Math.min(500, Math.max(1, maxResults)));
        if (nextCursor != null && !nextCursor.isBlank()) {
            options.put("next_cursor", nextCursor);
        }

        try {
            ApiResponse response = cloudinary.api().resources(options);
            return parse(response, resourceType);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list Cloudinary assets", exception);
        }
    }

    private AssetPage parse(Map<?, ?> response, String fallbackResourceType) {
        Object rawResources = response.get("resources");
        if (!(rawResources instanceof List<?> resources)) {
            throw new IllegalStateException("Cloudinary resources response is malformed");
        }

        List<Asset> assets = new ArrayList<>(resources.size());
        for (Object rawResource : resources) {
            if (!(rawResource instanceof Map<?, ?> resource)) {
                throw new IllegalStateException("Cloudinary asset response is malformed");
            }
            String publicId = resource.get("public_id") instanceof String s && !s.isBlank() ? s : null;
            if (publicId == null) {
                throw new IllegalStateException("Cloudinary asset is missing public_id");
            }
            String resourceType = resource.get("resource_type") instanceof String s && !s.isBlank() ? s : fallbackResourceType;
            Instant createdAt = parseInstant(resource.get("created_at"));

            assets.add(new Asset(publicId, resourceType, createdAt));
        }

        String nextCursor = response.get("next_cursor") instanceof String s && !s.isBlank() ? s : null;
        return new AssetPage(List.copyOf(assets), nextCursor);
    }

    private Instant parseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                return OffsetDateTime.parse(text).toInstant();
            }
        }
        throw new IllegalStateException("Cloudinary asset is missing or has invalid created_at");
    }

    public record Asset(String publicId, String resourceType, Instant createdAt) {
    }

    public record AssetPage(List<Asset> assets, String nextCursor) {
    }
}
