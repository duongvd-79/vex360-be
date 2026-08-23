package com.example.vex360.features.product.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.vex360.features.product.enums.ProductContentType;
import com.example.vex360.shared.enums.StorageProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "product_contents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductContent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    @Column(name = "content_url", nullable = false, length = 1000)
    String contentUrl;

    @Column(name = "public_id", nullable = false, length = 500)
    String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(50)")
    ProductContentType type;

    @Column(name = "order_index", nullable = false)
    Integer orderIndex;

    @Column(name = "mime_type", nullable = false, length = 100)
    String mimeType;

    @Column(name = "file_size", nullable = false)
    Long fileSize;

    /**
     * Dòng tạo trước khi có cột này mang giá trị null, nên mọi nơi đọc phải đi qua
     * {@link #resolveStorageProvider()} chứ đừng dùng getter thẳng.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", length = 30)
    StorageProvider storageProvider;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;

    /** Coi null là CLOUDINARY: cột được thêm sau nên dữ liệu cũ không có giá trị. */
    public StorageProvider resolveStorageProvider() {
        return storageProvider == null ? StorageProvider.CLOUDINARY : storageProvider;
    }
}
