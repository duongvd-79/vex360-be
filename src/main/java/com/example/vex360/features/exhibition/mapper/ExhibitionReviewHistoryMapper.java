package com.example.vex360.features.exhibition.mapper;

import java.time.LocalDate;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewHistoryResponseDTO;
import com.example.vex360.features.exhibition.dtos.response.ExhibitionReviewSnapshotResponseDTO;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.entities.ExhibitionReviewRequest;

@Mapper(componentModel = "spring")
public interface ExhibitionReviewHistoryMapper {

    @Mapping(target = "reviewedByName", source = "reviewedBy.fullName")
    @Mapping(target = "snapshot", expression = "java(parseSnapshot(request.getContentSnapshotJson()))")
    ExhibitionReviewHistoryResponseDTO toDTO(ExhibitionReviewRequest request);

    @Mapping(target = "keyVisualUrl", source = "keyVisualUrl")
    ExhibitionReviewSnapshotResponseDTO toSnapshot(Exhibition exhibition, String keyVisualUrl);

    default String toSnapshotJson(ExhibitionReviewSnapshotResponseDTO snapshot) {
        if (snapshot == null) {
            return null;
        }
        return String.format(
                "{\"name\":%s,\"category\":%s,\"description\":%s,\"startDate\":%s,\"endDate\":%s,\"estimatedBooths\":%s,\"keyVisualUrl\":%s}",
                toJsonString(snapshot.getName()),
                toJsonString(snapshot.getCategory()),
                toJsonString(snapshot.getDescription()),
                toJsonString(snapshot.getStartDate() != null ? snapshot.getStartDate().toString() : null),
                toJsonString(snapshot.getEndDate() != null ? snapshot.getEndDate().toString() : null),
                snapshot.getEstimatedBooths() != null ? snapshot.getEstimatedBooths().toString() : "null",
                toJsonString(snapshot.getKeyVisualUrl()));
    }

    default ExhibitionReviewSnapshotResponseDTO parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String name = extractJsonField(json, "name");
            String category = extractJsonField(json, "category");
            String description = extractJsonField(json, "description");
            String startDateStr = extractJsonField(json, "startDate");
            String endDateStr = extractJsonField(json, "endDate");
            String estimatedBoothsStr = extractJsonField(json, "estimatedBooths");
            String keyVisualUrl = extractJsonField(json, "keyVisualUrl");

            LocalDate startDate = (startDateStr != null && !startDateStr.equals("null")) ? LocalDate.parse(startDateStr)
                    : null;
            LocalDate endDate = (endDateStr != null && !endDateStr.equals("null")) ? LocalDate.parse(endDateStr) : null;
            Integer estimatedBooths = (estimatedBoothsStr != null && !estimatedBoothsStr.equals("null"))
                    ? Integer.parseInt(estimatedBoothsStr)
                    : null;

            return ExhibitionReviewSnapshotResponseDTO.builder()
                    .name(name)
                    .category(category)
                    .description(description)
                    .startDate(startDate)
                    .endDate(endDate)
                    .estimatedBooths(estimatedBooths)
                    .keyVisualUrl(keyVisualUrl)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJsonString(String val) {
        if (val == null)
            return "null";
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1)
            return null;
        int start = idx + key.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length())
            return null;
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
                    break;
                }
                end++;
            }
            return json.substring(start + 1, end)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r");
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            String val = json.substring(start, end).trim();
            return "null".equals(val) ? null : val;
        }
    }
}
