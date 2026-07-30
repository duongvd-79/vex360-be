package com.example.vex360.features.designrequest.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.vex360.features.booth.entities.Booth;
import com.example.vex360.features.booth.entities.Hotspot;
import com.example.vex360.features.booth.entities.Panorama;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeScope;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.ChangeType;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeItemDTO.FieldChangeDTO;
import com.example.vex360.features.designrequest.dtos.response.DesignDraftChangeSummaryDTO;
import com.example.vex360.features.designrequest.entities.DesignDraft;
import com.example.vex360.features.designrequest.entities.DesignDraftHotspot;
import com.example.vex360.features.designrequest.entities.DesignDraftMediaAsset;
import com.example.vex360.features.designrequest.entities.DesignDraftPanorama;

@Service
public class DesignDraftDiffService {

    public DesignDraftChangeSummaryDTO compareDraftWithPrevious(DesignDraft current, DesignDraft previous) {
        List<DesignDraftChangeItemDTO> items = new ArrayList<>();
        if (current == null || previous == null) {
            return buildSummaryFromItems(items);
        }

        // 1. Compare Settings (Name, Description, Template, Thumbnail, Music)
        compareSettings(current, previous, items);

        // 2. Compare Panoramas
        comparePanoramasBetweenDrafts(current.getPanoramas(), previous.getPanoramas(), items);

        // 3. Compare Hotspots (Deep field comparison)
        compareHotspotsBetweenDrafts(current.getPanoramas(), previous.getPanoramas(), items);

        // 4. Compare Linked Products in Hotspots
        compareProductsBetweenDrafts(current.getPanoramas(), previous.getPanoramas(), items);

        // 5. Compare Linked Media Assets in Hotspots
        compareMediaAssetsBetweenDrafts(current, previous, items);

        return buildSummaryFromItems(items);
    }

    public DesignDraftChangeSummaryDTO compareDraftWithBooth(DesignDraft current, Booth booth) {
        List<DesignDraftChangeItemDTO> items = new ArrayList<>();
        if (current == null || booth == null) {
            return buildSummaryFromItems(items);
        }

        // 1. Compare Booth Settings (Name, Description)
        compareSettingsWithBooth(current, booth, items);

        // 2. Compare Panoramas (Draft vs Booth)
        comparePanoramasDraftWithBooth(current.getPanoramas(), booth.getPanoramas(), items);

        // 3. Compare Hotspots (Draft vs Booth)
        compareHotspotsDraftWithBooth(current.getPanoramas(), booth.getPanoramas(), items);

        // 4. Compare Linked Products (Draft vs Booth)
        compareProductsDraftWithBooth(current.getPanoramas(), booth.getPanoramas(), items);

        // 5. Compare Linked Media Assets (Draft vs Booth)
        compareMediaAssetsDraftWithBooth(current, booth, items);

        return buildSummaryFromItems(items);
    }

    // ==========================================
    // 1. SETTINGS COMPARISON
    // ==========================================
    private void compareSettings(DesignDraft current, DesignDraft previous, List<DesignDraftChangeItemDTO> items) {
        List<FieldChangeDTO> changes = new ArrayList<>();
        if (!Objects.equals(current.getBoothName(), previous.getBoothName())) {
            changes.add(new FieldChangeDTO("boothName", previous.getBoothName(), current.getBoothName()));
        }
        if (!Objects.equals(current.getBoothDescription(), previous.getBoothDescription())) {
            changes.add(new FieldChangeDTO("boothDescription", previous.getBoothDescription(), current.getBoothDescription()));
        }
        if (!Objects.equals(current.getDisplayTemplateKey(), previous.getDisplayTemplateKey())) {
            changes.add(new FieldChangeDTO("displayTemplateKey", previous.getDisplayTemplateKey(), current.getDisplayTemplateKey()));
        }
        if (!Objects.equals(current.getThumbnailAction(), previous.getThumbnailAction())) {
            changes.add(new FieldChangeDTO("thumbnailAction", safeString(previous.getThumbnailAction()), safeString(current.getThumbnailAction())));
        }
        if (!Objects.equals(current.getBackgroundMusicAction(), previous.getBackgroundMusicAction())) {
            changes.add(new FieldChangeDTO("backgroundMusicAction", safeString(previous.getBackgroundMusicAction()), safeString(current.getBackgroundMusicAction())));
        }
        if (!changes.isEmpty()) {
            items.add(DesignDraftChangeItemDTO.builder()
                    .scope(ChangeScope.BOOTH_SETTINGS)
                    .changeType(ChangeType.MODIFIED)
                    .targetName("Booth Settings")
                    .fieldChanges(changes)
                    .build());
        }
    }

    private void compareSettingsWithBooth(DesignDraft current, Booth booth, List<DesignDraftChangeItemDTO> items) {
        List<FieldChangeDTO> changes = new ArrayList<>();
        if (!Objects.equals(current.getBoothName(), booth.getName())) {
            changes.add(new FieldChangeDTO("boothName", booth.getName(), current.getBoothName()));
        }
        if (!Objects.equals(current.getBoothDescription(), booth.getDescription())) {
            changes.add(new FieldChangeDTO("boothDescription", booth.getDescription(), current.getBoothDescription()));
        }
        if (!changes.isEmpty()) {
            items.add(DesignDraftChangeItemDTO.builder()
                    .scope(ChangeScope.BOOTH_SETTINGS)
                    .changeType(ChangeType.MODIFIED)
                    .targetName("Booth Settings")
                    .fieldChanges(changes)
                    .build());
        }
    }

    // ==========================================
    // 2. PANORAMA COMPARISON
    // ==========================================
    private void comparePanoramasBetweenDrafts(
            List<DesignDraftPanorama> currentList,
            List<DesignDraftPanorama> previousList,
            List<DesignDraftChangeItemDTO> items) {
        List<DesignDraftPanorama> currSafe = currentList == null ? List.of() : currentList;
        List<DesignDraftPanorama> prevSafe = previousList == null ? List.of() : previousList;

        Map<String, DesignDraftPanorama> prevMap = new LinkedHashMap<>();
        prevSafe.forEach(p -> prevMap.put(draftPanoKey(p), p));

        Map<String, DesignDraftPanorama> currMap = new LinkedHashMap<>();
        currSafe.forEach(p -> currMap.put(draftPanoKey(p), p));

        for (DesignDraftPanorama curr : currSafe) {
            String key = draftPanoKey(curr);
            DesignDraftPanorama prev = prevMap.get(key);
            if (prev == null) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PANORAMA)
                        .changeType(ChangeType.ADDED)
                        .targetName(curr.getName())
                        .fieldChanges(List.of(new FieldChangeDTO("fileSizeImpact", null, "0 Bytes")))
                        .build());
            } else {
                List<FieldChangeDTO> changes = new ArrayList<>();
                if (!Objects.equals(curr.getName(), prev.getName())) {
                    changes.add(new FieldChangeDTO("name", prev.getName(), curr.getName()));
                }
                if (!Objects.equals(curr.getOrderIndex(), prev.getOrderIndex())) {
                    changes.add(new FieldChangeDTO("orderIndex", safeString(prev.getOrderIndex()), safeString(curr.getOrderIndex())));
                }
                if (!Objects.equals(curr.getIsDefault(), prev.getIsDefault())) {
                    changes.add(new FieldChangeDTO("isDefault", safeString(prev.getIsDefault()), safeString(curr.getIsDefault())));
                }
                if (!Objects.equals(curr.getImageUrl(), prev.getImageUrl())) {
                    changes.add(new FieldChangeDTO("imageUrl", prev.getImageUrl(), curr.getImageUrl()));
                }
                if (!changes.isEmpty()) {
                    items.add(DesignDraftChangeItemDTO.builder()
                            .scope(ChangeScope.PANORAMA)
                            .changeType(ChangeType.MODIFIED)
                            .targetName(curr.getName())
                            .fieldChanges(changes)
                            .build());
                }
            }
        }

        for (DesignDraftPanorama prev : prevSafe) {
            String key = draftPanoKey(prev);
            if (!currMap.containsKey(key)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PANORAMA)
                        .changeType(ChangeType.REMOVED)
                        .targetName(prev.getName())
                        .fieldChanges(List.of(new FieldChangeDTO("fileSizeImpact", null, "0 Bytes")))
                        .build());
            }
        }
    }

    private void comparePanoramasDraftWithBooth(
            List<DesignDraftPanorama> currentList,
            List<Panorama> boothList,
            List<DesignDraftChangeItemDTO> items) {
        List<DesignDraftPanorama> currSafe = currentList == null ? List.of() : currentList;
        List<Panorama> boothSafe = boothList == null ? List.of() : boothList;

        Map<String, Panorama> boothMap = new LinkedHashMap<>();
        boothSafe.forEach(p -> boothMap.put(boothPanoKey(p), p));

        Map<String, DesignDraftPanorama> currMap = new LinkedHashMap<>();
        currSafe.forEach(p -> currMap.put(draftPanoKey(p), p));

        for (DesignDraftPanorama curr : currSafe) {
            String key = draftPanoKey(curr);
            Panorama prev = boothMap.get(key);
            if (prev == null) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PANORAMA)
                        .changeType(ChangeType.ADDED)
                        .targetName(curr.getName())
                        .fieldChanges(List.of(new FieldChangeDTO("fileSizeImpact", null, "0 Bytes")))
                        .build());
            } else {
                List<FieldChangeDTO> changes = new ArrayList<>();
                if (!Objects.equals(curr.getName(), prev.getName())) {
                    changes.add(new FieldChangeDTO("name", prev.getName(), curr.getName()));
                }
                if (!Objects.equals(curr.getOrderIndex(), prev.getOrderIndex())) {
                    changes.add(new FieldChangeDTO("orderIndex", safeString(prev.getOrderIndex()), safeString(curr.getOrderIndex())));
                }
                if (!Objects.equals(curr.getIsDefault(), prev.getIsDefault())) {
                    changes.add(new FieldChangeDTO("isDefault", safeString(prev.getIsDefault()), safeString(curr.getIsDefault())));
                }
                if (!changes.isEmpty()) {
                    items.add(DesignDraftChangeItemDTO.builder()
                            .scope(ChangeScope.PANORAMA)
                            .changeType(ChangeType.MODIFIED)
                            .targetName(curr.getName())
                            .fieldChanges(changes)
                            .build());
                }
            }
        }

        for (Panorama bp : boothSafe) {
            String key = boothPanoKey(bp);
            if (!currMap.containsKey(key)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PANORAMA)
                        .changeType(ChangeType.REMOVED)
                        .targetName(bp.getName())
                        .fieldChanges(List.of(new FieldChangeDTO("fileSizeImpact", null, "0 Bytes")))
                        .build());
            }
        }
    }

    // ==========================================
    // 3. HOTSPOT COMPARISON
    // ==========================================
    private void compareHotspotsBetweenDrafts(
            List<DesignDraftPanorama> currentPanoramas,
            List<DesignDraftPanorama> previousPanoramas,
            List<DesignDraftChangeItemDTO> items) {
        List<DesignDraftPanorama> currPanos = currentPanoramas == null ? List.of() : currentPanoramas;
        List<DesignDraftPanorama> prevPanos = previousPanoramas == null ? List.of() : previousPanoramas;

        Map<String, DraftHotspotPair> prevMap = new LinkedHashMap<>();
        for (DesignDraftPanorama p : prevPanos) {
            String pKey = draftPanoKey(p);
            if (p.getHotspots() != null) {
                for (DesignDraftHotspot h : p.getHotspots()) {
                    prevMap.put(draftHotspotKey(pKey, h), new DraftHotspotPair(p, h));
                }
            }
        }

        Map<String, DraftHotspotPair> currMap = new LinkedHashMap<>();
        for (DesignDraftPanorama p : currPanos) {
            String pKey = draftPanoKey(p);
            if (p.getHotspots() != null) {
                for (DesignDraftHotspot h : p.getHotspots()) {
                    currMap.put(draftHotspotKey(pKey, h), new DraftHotspotPair(p, h));
                }
            }
        }

        for (Map.Entry<String, DraftHotspotPair> entry : currMap.entrySet()) {
            String key = entry.getKey();
            DraftHotspotPair currPair = entry.getValue();
            DraftHotspotPair prevPair = prevMap.get(key);

            if (prevPair == null) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.HOTSPOT)
                        .changeType(ChangeType.ADDED)
                        .targetName(currPair.hotspot.getName() != null ? currPair.hotspot.getName() : "Hotspot")
                        .fieldChanges(List.of(new FieldChangeDTO("type", null, safeString(currPair.hotspot.getType()))))
                        .build());
            } else {
                List<FieldChangeDTO> changes = compareDraftHotspotFields(currPair.hotspot, prevPair.hotspot);
                if (!changes.isEmpty()) {
                    items.add(DesignDraftChangeItemDTO.builder()
                            .scope(ChangeScope.HOTSPOT)
                            .changeType(ChangeType.MODIFIED)
                            .targetName(currPair.hotspot.getName() != null ? currPair.hotspot.getName() : "Hotspot")
                            .fieldChanges(changes)
                            .build());
                }
            }
        }

        for (Map.Entry<String, DraftHotspotPair> entry : prevMap.entrySet()) {
            if (!currMap.containsKey(entry.getKey())) {
                DesignDraftHotspot h = entry.getValue().hotspot;
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.HOTSPOT)
                        .changeType(ChangeType.REMOVED)
                        .targetName(h.getName() != null ? h.getName() : "Hotspot")
                        .build());
            }
        }
    }

    private void compareHotspotsDraftWithBooth(
            List<DesignDraftPanorama> currentPanoramas,
            List<Panorama> boothPanoramas,
            List<DesignDraftChangeItemDTO> items) {
        List<DesignDraftPanorama> currPanos = currentPanoramas == null ? List.of() : currentPanoramas;
        List<Panorama> boothPanos = boothPanoramas == null ? List.of() : boothPanoramas;

        Map<String, BoothHotspotPair> boothMap = new LinkedHashMap<>();
        for (Panorama p : boothPanos) {
            String pKey = boothPanoKey(p);
            if (p.getHotspots() != null) {
                for (Hotspot h : p.getHotspots()) {
                    boothMap.put(boothHotspotKey(pKey, h), new BoothHotspotPair(p, h));
                }
            }
        }

        Map<String, DraftHotspotPair> currMap = new LinkedHashMap<>();
        for (DesignDraftPanorama p : currPanos) {
            String pKey = draftPanoKey(p);
            if (p.getHotspots() != null) {
                for (DesignDraftHotspot h : p.getHotspots()) {
                    currMap.put(draftHotspotKey(pKey, h), new DraftHotspotPair(p, h));
                }
            }
        }

        for (Map.Entry<String, DraftHotspotPair> entry : currMap.entrySet()) {
            String key = entry.getKey();
            DraftHotspotPair currPair = entry.getValue();
            BoothHotspotPair boothPair = boothMap.get(key);

            if (boothPair == null) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.HOTSPOT)
                        .changeType(ChangeType.ADDED)
                        .targetName(currPair.hotspot.getName() != null ? currPair.hotspot.getName() : "Hotspot")
                        .fieldChanges(List.of(new FieldChangeDTO("type", null, safeString(currPair.hotspot.getType()))))
                        .build());
            } else {
                List<FieldChangeDTO> changes = compareDraftAndBoothHotspotFields(currPair.hotspot, boothPair.hotspot);
                if (!changes.isEmpty()) {
                    items.add(DesignDraftChangeItemDTO.builder()
                            .scope(ChangeScope.HOTSPOT)
                            .changeType(ChangeType.MODIFIED)
                            .targetName(currPair.hotspot.getName() != null ? currPair.hotspot.getName() : "Hotspot")
                            .fieldChanges(changes)
                            .build());
                }
            }
        }

        for (Map.Entry<String, BoothHotspotPair> entry : boothMap.entrySet()) {
            if (!currMap.containsKey(entry.getKey())) {
                Hotspot h = entry.getValue().hotspot;
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.HOTSPOT)
                        .changeType(ChangeType.REMOVED)
                        .targetName(h.getName() != null ? h.getName() : "Hotspot")
                        .build());
            }
        }
    }

    private List<FieldChangeDTO> compareDraftHotspotFields(DesignDraftHotspot curr, DesignDraftHotspot prev) {
        List<FieldChangeDTO> changes = new ArrayList<>();
        if (!Objects.equals(curr.getName(), prev.getName())) {
            changes.add(new FieldChangeDTO("name", prev.getName(), curr.getName()));
        }
        if (curr.getType() != prev.getType()) {
            changes.add(new FieldChangeDTO("type", safeString(prev.getType()), safeString(curr.getType())));
        }
        String prevPos = formatPos(prev.getXPosition(), prev.getYPosition(), prev.getZPosition());
        String currPos = formatPos(curr.getXPosition(), curr.getYPosition(), curr.getZPosition());
        if (!Objects.equals(prevPos, currPos)) {
            changes.add(new FieldChangeDTO("position", prevPos, currPos));
        }
        if (!Objects.equals(curr.getTargetDraftPanoramaKey(), prev.getTargetDraftPanoramaKey())) {
            changes.add(new FieldChangeDTO("targetPanorama", prev.getTargetDraftPanoramaKey(), curr.getTargetDraftPanoramaKey()));
        }
        String prevProd = prev.getProduct() == null ? null : prev.getProduct().getName();
        String currProd = curr.getProduct() == null ? null : curr.getProduct().getName();
        if (!Objects.equals(currProd, prevProd)) {
            changes.add(new FieldChangeDTO("product", prevProd, currProd));
        }
        String prevMedia = draftMediaName(prev);
        String currMedia = draftMediaName(curr);
        if (!Objects.equals(currMedia, prevMedia)) {
            changes.add(new FieldChangeDTO("mediaAsset", prevMedia, currMedia));
        }
        if (!Objects.equals(curr.getInfoText(), prev.getInfoText())) {
            changes.add(new FieldChangeDTO("infoText", prev.getInfoText(), curr.getInfoText()));
        }
        if (!Objects.equals(curr.getIconStyle(), prev.getIconStyle())) {
            changes.add(new FieldChangeDTO("iconStyle", prev.getIconStyle(), curr.getIconStyle()));
        }
        if (!Objects.equals(curr.getScale(), prev.getScale())) {
            changes.add(new FieldChangeDTO("scale", safeString(prev.getScale()), safeString(curr.getScale())));
        }
        if (curr.getMediaClickAction() != prev.getMediaClickAction()) {
            changes.add(new FieldChangeDTO("mediaClickAction", safeString(prev.getMediaClickAction()), safeString(curr.getMediaClickAction())));
        }
        if (curr.getInfoContentType() != prev.getInfoContentType()) {
            changes.add(new FieldChangeDTO("infoContentType", safeString(prev.getInfoContentType()), safeString(curr.getInfoContentType())));
        }
        return changes;
    }

    private List<FieldChangeDTO> compareDraftAndBoothHotspotFields(DesignDraftHotspot curr, Hotspot prev) {
        List<FieldChangeDTO> changes = new ArrayList<>();
        if (!Objects.equals(curr.getName(), prev.getName())) {
            changes.add(new FieldChangeDTO("name", prev.getName(), curr.getName()));
        }
        if (curr.getType() != prev.getType()) {
            changes.add(new FieldChangeDTO("type", safeString(prev.getType()), safeString(curr.getType())));
        }
        String prevPos = formatPos(prev.getXPosition(), prev.getYPosition(), prev.getZPosition());
        String currPos = formatPos(curr.getXPosition(), curr.getYPosition(), curr.getZPosition());
        if (!Objects.equals(prevPos, currPos)) {
            changes.add(new FieldChangeDTO("position", prevPos, currPos));
        }
        String prevTarget = prev.getTargetPanorama() == null ? null : prev.getTargetPanorama().getName();
        String currTarget = curr.getTargetDraftPanoramaKey();
        if (!Objects.equals(currTarget, prevTarget)) {
            changes.add(new FieldChangeDTO("targetPanorama", prevTarget, currTarget));
        }
        String prevProd = prev.getProduct() == null ? null : prev.getProduct().getName();
        String currProd = curr.getProduct() == null ? null : curr.getProduct().getName();
        if (!Objects.equals(currProd, prevProd)) {
            changes.add(new FieldChangeDTO("product", prevProd, currProd));
        }
        String prevMedia = prev.getMediaAsset() == null ? null : prev.getMediaAsset().getName();
        String currMedia = draftMediaName(curr);
        if (!Objects.equals(currMedia, prevMedia)) {
            changes.add(new FieldChangeDTO("mediaAsset", prevMedia, currMedia));
        }
        if (!Objects.equals(curr.getInfoText(), prev.getInfoText())) {
            changes.add(new FieldChangeDTO("infoText", prev.getInfoText(), curr.getInfoText()));
        }
        if (!Objects.equals(curr.getIconStyle(), prev.getIconStyle())) {
            changes.add(new FieldChangeDTO("iconStyle", prev.getIconStyle(), curr.getIconStyle()));
        }
        if (!Objects.equals(curr.getScale(), prev.getScale())) {
            changes.add(new FieldChangeDTO("scale", safeString(prev.getScale()), safeString(curr.getScale())));
        }
        return changes;
    }

    // ==========================================
    // 4. PRODUCT COMPARISON
    // ==========================================
    private void compareProductsBetweenDrafts(
            List<DesignDraftPanorama> currentPanoramas,
            List<DesignDraftPanorama> previousPanoramas,
            List<DesignDraftChangeItemDTO> items) {
        Set<String> currProducts = extractDraftProducts(currentPanoramas);
        Set<String> prevProducts = extractDraftProducts(previousPanoramas);

        for (String p : currProducts) {
            if (!prevProducts.contains(p)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PRODUCT)
                        .changeType(ChangeType.ADDED)
                        .targetName(p)
                        .build());
            }
        }
        for (String p : prevProducts) {
            if (!currProducts.contains(p)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PRODUCT)
                        .changeType(ChangeType.REMOVED)
                        .targetName(p)
                        .build());
            }
        }
    }

    private void compareProductsDraftWithBooth(
            List<DesignDraftPanorama> currentPanoramas,
            List<Panorama> boothPanoramas,
            List<DesignDraftChangeItemDTO> items) {
        Set<String> currProducts = extractDraftProducts(currentPanoramas);
        Set<String> boothProducts = extractBoothProducts(boothPanoramas);

        for (String p : currProducts) {
            if (!boothProducts.contains(p)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PRODUCT)
                        .changeType(ChangeType.ADDED)
                        .targetName(p)
                        .build());
            }
        }
        for (String p : boothProducts) {
            if (!currProducts.contains(p)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.PRODUCT)
                        .changeType(ChangeType.REMOVED)
                        .targetName(p)
                        .build());
            }
        }
    }

    // ==========================================
    // 5. MEDIA ASSET COMPARISON
    // ==========================================
    private void compareMediaAssetsBetweenDrafts(DesignDraft current, DesignDraft previous, List<DesignDraftChangeItemDTO> items) {
        Set<String> currMedia = extractDraftMediaAssets(current);
        Set<String> prevMedia = extractDraftMediaAssets(previous);

        for (String m : currMedia) {
            if (!prevMedia.contains(m)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.MEDIA_ASSET)
                        .changeType(ChangeType.ADDED)
                        .targetName(m)
                        .build());
            }
        }
        for (String m : prevMedia) {
            if (!currMedia.contains(m)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.MEDIA_ASSET)
                        .changeType(ChangeType.REMOVED)
                        .targetName(m)
                        .build());
            }
        }
    }

    private void compareMediaAssetsDraftWithBooth(DesignDraft current, Booth booth, List<DesignDraftChangeItemDTO> items) {
        Set<String> currMedia = extractDraftMediaAssets(current);
        Set<String> boothMedia = extractBoothMediaAssets(booth.getPanoramas());

        for (String m : currMedia) {
            if (!boothMedia.contains(m)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.MEDIA_ASSET)
                        .changeType(ChangeType.ADDED)
                        .targetName(m)
                        .build());
            }
        }
        for (String m : boothMedia) {
            if (!currMedia.contains(m)) {
                items.add(DesignDraftChangeItemDTO.builder()
                        .scope(ChangeScope.MEDIA_ASSET)
                        .changeType(ChangeType.REMOVED)
                        .targetName(m)
                        .build());
            }
        }
    }

    // ==========================================
    // UTILITIES & KEYS
    // ==========================================
    private String draftPanoKey(DesignDraftPanorama p) {
        if (p == null) return "";
        if (p.getClientKey() != null && !p.getClientKey().isBlank()) return p.getClientKey();
        if (p.getImageKey() != null && !p.getImageKey().isBlank()) return p.getImageKey();
        if (p.getId() != null) return p.getId().toString();
        return p.getName() == null ? "" : p.getName();
    }

    private String boothPanoKey(Panorama p) {
        if (p == null) return "";
        if (p.getImageKey() != null && !p.getImageKey().isBlank()) return p.getImageKey();
        if (p.getId() != null) return p.getId().toString();
        return p.getName() == null ? "" : p.getName();
    }

    private String draftHotspotKey(String panoKey, DesignDraftHotspot h) {
        if (h == null) return panoKey + ":null";
        if (h.getName() != null && !h.getName().isBlank()) return panoKey + ":" + h.getName();
        if (h.getId() != null) return panoKey + ":" + h.getId();
        return panoKey + ":" + formatPos(h.getXPosition(), h.getYPosition(), h.getZPosition());
    }

    private String boothHotspotKey(String panoKey, Hotspot h) {
        if (h == null) return panoKey + ":null";
        if (h.getName() != null && !h.getName().isBlank()) return panoKey + ":" + h.getName();
        if (h.getId() != null) return panoKey + ":" + h.getId();
        return panoKey + ":" + formatPos(h.getXPosition(), h.getYPosition(), h.getZPosition());
    }

    private String draftMediaName(DesignDraftHotspot h) {
        if (h == null) return null;
        if (h.getMediaAsset() != null) return h.getMediaAsset().getName();
        if (h.getDesignDraftMediaAsset() != null && h.getDesignDraftMediaAsset().getAsset() != null) {
            return h.getDesignDraftMediaAsset().getAsset().getFileName();
        }
        return null;
    }

    private Set<String> extractDraftProducts(List<DesignDraftPanorama> panoramas) {
        Set<String> set = new HashSet<>();
        if (panoramas != null) {
            for (DesignDraftPanorama p : panoramas) {
                if (p.getHotspots() != null) {
                    for (DesignDraftHotspot h : p.getHotspots()) {
                        if (h.getProduct() != null && h.getProduct().getName() != null) {
                            set.add(h.getProduct().getName());
                        }
                    }
                }
            }
        }
        return set;
    }

    private Set<String> extractBoothProducts(List<Panorama> panoramas) {
        Set<String> set = new HashSet<>();
        if (panoramas != null) {
            for (Panorama p : panoramas) {
                if (p.getHotspots() != null) {
                    for (Hotspot h : p.getHotspots()) {
                        if (h.getProduct() != null && h.getProduct().getName() != null) {
                            set.add(h.getProduct().getName());
                        }
                    }
                }
            }
        }
        return set;
    }

    private Set<String> extractDraftMediaAssets(DesignDraft draft) {
        Set<String> set = new HashSet<>();
        if (draft != null && draft.getPanoramas() != null) {
            for (DesignDraftPanorama p : draft.getPanoramas()) {
                if (p.getHotspots() != null) {
                    for (DesignDraftHotspot h : p.getHotspots()) {
                        String name = draftMediaName(h);
                        if (name != null && !name.isBlank()) {
                            set.add(name);
                        }
                    }
                }
            }
        }
        if (draft != null && draft.getMediaAssets() != null) {
            for (DesignDraftMediaAsset ma : draft.getMediaAssets()) {
                if (ma.getAsset() != null && ma.getAsset().getFileName() != null) {
                    set.add(ma.getAsset().getFileName());
                }
            }
        }
        return set;
    }

    private Set<String> extractBoothMediaAssets(List<Panorama> panoramas) {
        Set<String> set = new HashSet<>();
        if (panoramas != null) {
            for (Panorama p : panoramas) {
                if (p.getHotspots() != null) {
                    for (Hotspot h : p.getHotspots()) {
                        if (h.getMediaAsset() != null && h.getMediaAsset().getName() != null) {
                            set.add(h.getMediaAsset().getName());
                        }
                    }
                }
            }
        }
        return set;
    }

    private String formatPos(Double x, Double y, Double z) {
        if (x == null || y == null || z == null) return "(0.0,0.0,0.0)";
        return String.format("(%.2f,%.2f,%.2f)", x, y, z);
    }

    private String safeString(Object val) {
        return val == null ? "" : val.toString();
    }

    private DesignDraftChangeSummaryDTO buildSummaryFromItems(List<DesignDraftChangeItemDTO> items) {
        int added = (int) items.stream().filter(i -> i.getChangeType() == ChangeType.ADDED).count();
        int modified = (int) items.stream().filter(i -> i.getChangeType() == ChangeType.MODIFIED).count();
        int removed = (int) items.stream().filter(i -> i.getChangeType() == ChangeType.REMOVED).count();
        return DesignDraftChangeSummaryDTO.builder()
                .addedCount(added)
                .modifiedCount(modified)
                .removedCount(removed)
                .totalCount(items.size())
                .items(items)
                .build();
    }

    private static class DraftHotspotPair {
        final DesignDraftHotspot hotspot;
        DraftHotspotPair(DesignDraftPanorama panorama, DesignDraftHotspot hotspot) {
            this.hotspot = hotspot;
        }
    }

    private static class BoothHotspotPair {
        final Hotspot hotspot;
        BoothHotspotPair(Panorama panorama, Hotspot hotspot) {
            this.hotspot = hotspot;
        }
    }
}
