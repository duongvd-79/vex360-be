package com.example.vex360.features.booth.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.vex360.features.booth.entities.MediaAsset;
import com.example.vex360.features.booth.enums.MediaAssetType;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Page<MediaAsset> findByCompanyId(UUID companyId, Pageable pageable);

    Page<MediaAsset> findByCompanyIdAndType(UUID companyId, MediaAssetType type, Pageable pageable);

    Optional<MediaAsset> findByIdAndCompanyId(UUID id, UUID companyId);

    List<MediaAsset> findByIdInAndCompanyId(List<UUID> ids, UUID companyId);

    boolean existsByPublicId(String publicId);

    /**
     * Tổng dung lượng các tệp media của một công ty. Tính ở DB thay vì tải danh
     * sách về rồi cộng ở client — danh sách này có phân trang nên cộng ở client
     * sẽ chỉ ra tổng của trang đầu tiên.
     */
    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM MediaAsset m WHERE m.company.id = :companyId")
    long sumFileSizeByCompanyId(@Param("companyId") UUID companyId);

    boolean existsByIdAndCompanyId(UUID id, UUID companyId);
}
