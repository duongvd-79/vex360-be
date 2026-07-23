package com.example.vex360.features.designrequest.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.designrequest.entities.DesignDraft;

public interface DesignDraftRepository extends JpaRepository<DesignDraft, UUID> {
    Optional<DesignDraft> findByDesignRequestIdAndVersionNumber(UUID designRequestId, Integer versionNumber);

    Optional<DesignDraft> findFirstByDesignRequestIdOrderByVersionNumberDesc(UUID designRequestId);

    Optional<DesignDraft> findFirstByDesignRequestIdAndVersionNumberGreaterThanOrderByVersionNumberDesc(
            UUID designRequestId,
            Integer versionNumber);

    @Query("""
            SELECT CASE WHEN COUNT(draft) > 0 THEN true ELSE false END
            FROM DesignDraft draft
            LEFT JOIN draft.thumbnailAsset thumbnailAsset
            LEFT JOIN draft.backgroundMusicAsset backgroundMusicAsset
            LEFT JOIN draft.mediaAssets mediaAsset
            LEFT JOIN mediaAsset.asset draftAsset
            WHERE thumbnailAsset.publicId = :publicId
               OR backgroundMusicAsset.publicId = :publicId
               OR draftAsset.publicId = :publicId
            """)
    boolean existsByThumbnailOrBackgroundMusicPublicId(@Param("publicId") String publicId);
}
