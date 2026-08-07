package com.example.vex360.features.designrequest.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.vex360.features.designrequest.entities.DesignDraftAsset;

public interface DesignDraftAssetRepository extends JpaRepository<DesignDraftAsset, UUID> {
    boolean existsByPublicId(String publicId);

    Optional<DesignDraftAsset> findByIdAndDesignRequestId(UUID id, UUID designRequestId);

    Optional<DesignDraftAsset> findByDesignRequestIdAndPublicId(UUID designRequestId, String publicId);

    Optional<DesignDraftAsset> findFirstByPublicIdOrderByCreatedAtDesc(String publicId);

    List<DesignDraftAsset> findByDesignRequestId(UUID designRequestId);

    Page<DesignDraftAsset> findByDesignRequestId(UUID designRequestId, Pageable pageable);

    List<DesignDraftAsset> findByDesignRequestBoothId(UUID boothId);
}
