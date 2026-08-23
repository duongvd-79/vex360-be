package com.example.vex360.features.hall.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.vex360.features.hall.dtos.response.HallReviewChangeSummaryDTO;
import com.example.vex360.features.hall.entities.HallReviewRequest;
import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.hall.enums.HallHotspotType;
import com.example.vex360.features.hall.enums.HallInfoContentType;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class HallReviewDiffService {
    private final ObjectMapper objectMapper;

    public HallReviewChangeSummaryDTO buildSummary(
            HallReviewSnapshot current,
            HallReviewRequest previousRequest,
            int versionNumber) {
        HallReviewSnapshot previous = previousRequest == null
                ? null
                : readSnapshot(previousRequest.getContentSnapshotJson());
        List<String> changedSections = new ArrayList<>();
        if (previous != null) {
            addIfChanged(changedSections, "HALL", previous.getHall(), current.getHall());
            addIfChanged(changedSections, "PANORAMAS", previous.getPanoramas(), current.getPanoramas());
            addIfChanged(changedSections, "HOTSPOTS", previous.getHotspots(), current.getHotspots());
            addIfChanged(changedSections, "ITEMS", previous.getItems(), current.getItems());
            addIfChanged(changedSections, "MEDIA_ASSETS", previous.getMediaAssets(), current.getMediaAssets());
        }
        return HallReviewChangeSummaryDTO.builder()
                .initialSubmission(previousRequest == null)
                .versionNumber(versionNumber)
                .comparedToRequestId(previousRequest == null ? null : previousRequest.getId())
                .comparedToVersionNumber(previousRequest == null ? null : previousRequest.getVersionNumber())
                .panoramaCount(size(current.getPanoramas()))
                .hotspotCount(size(current.getHotspots()))
                .itemCount(size(current.getItems()))
                .mediaAssetCount(size(current.getMediaAssets()))
                .changedSections(changedSections)
                .build();
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    public HallReviewSnapshot readSnapshot(String json) {
        HallReviewSnapshot snapshot = read(json, HallReviewSnapshot.class);
        normalizeInfoContentTypes(snapshot);
        return snapshot;
    }

    public HallReviewChangeSummaryDTO readSummary(String json) {
        return read(json, HallReviewChangeSummaryDTO.class);
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new AppException(ErrorCode.UNCATCHED_EXCEPTION);
        }
    }

    private void addIfChanged(List<String> sections, String section, Object previous, Object current) {
        if (!Objects.equals(previous, current)) {
            sections.add(section);
        }
    }

    private void normalizeInfoContentTypes(HallReviewSnapshot snapshot) {
        if (snapshot == null || snapshot.getHotspots() == null) {
            return;
        }
        Map<UUID, MediaAssetType> mediaTypes = new HashMap<>();
        if (snapshot.getMediaAssets() != null) {
            snapshot.getMediaAssets().forEach(media -> mediaTypes.put(media.getId(), media.getType()));
        }
        for (HallReviewSnapshot.HotspotItem hotspot : snapshot.getHotspots()) {
            if (hotspot.getType() != HallHotspotType.INFO || hotspot.getInfoContentType() != null) {
                continue;
            }
            if (hotspot.getItemId() != null) {
                hotspot.setInfoContentType(HallInfoContentType.ITEM);
            } else if (hotspot.getMediaAssetId() != null) {
                hotspot.setInfoContentType(mediaTypes.get(hotspot.getMediaAssetId()) == MediaAssetType.VIDEO
                        ? HallInfoContentType.VIDEO
                        : HallInfoContentType.IMAGE);
            } else if (hotspot.getInfoText() != null && !hotspot.getInfoText().isBlank()) {
                hotspot.setInfoContentType(HallInfoContentType.TEXT);
            } else {
                hotspot.setInfoContentType(HallInfoContentType.NONE);
            }
        }
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
