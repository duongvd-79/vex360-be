package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.vex360.features.booth.enums.MediaAssetType;
import com.example.vex360.features.designrequest.entities.DesignRequestMediaAsset;
import com.example.vex360.shared.enums.DesignRequestStatus;

public interface DesignRequestMediaAssetRepository extends JpaRepository<DesignRequestMediaAsset, UUID> {
    boolean existsByDesignRequestIdAndMediaAssetId(UUID requestId, UUID mediaAssetId);

    @Query(value = """
            SELECT drma FROM DesignRequestMediaAsset drma
            JOIN FETCH drma.mediaAsset mediaAsset
            JOIN FETCH mediaAsset.company
            WHERE drma.designRequest.id = :requestId
              AND (:type IS NULL OR mediaAsset.type = :type)
            """, countQuery = """
            SELECT COUNT(drma) FROM DesignRequestMediaAsset drma
            WHERE drma.designRequest.id = :requestId
              AND (:type IS NULL OR drma.mediaAsset.type = :type)
            """)
    Page<DesignRequestMediaAsset> searchAllowedMediaAssets(
            @Param("requestId") UUID requestId,
            @Param("type") MediaAssetType type,
            Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(drma) > 0 THEN true ELSE false END
            FROM DesignRequestMediaAsset drma
            WHERE drma.mediaAsset.id = :mediaAssetId
              AND drma.designRequest.status IN :statuses
            """)
    boolean existsByMediaAssetIdAndRequestStatusIn(
            @Param("mediaAssetId") UUID mediaAssetId,
            @Param("statuses") List<DesignRequestStatus> statuses);
}
