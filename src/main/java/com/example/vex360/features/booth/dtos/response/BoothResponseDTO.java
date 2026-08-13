package com.example.vex360.features.booth.dtos.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.shared.enums.BoothListingPriority;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoothResponseDTO {
    private UUID id;
    private UUID companyId;
    // Id của user exhibitor sở hữu gian hàng - dùng ở FE để mở phòng chat trực tiếp
    // với gian hàng
    private UUID exhibitorUserId;
    private UUID registrationUuid;
    private UUID exhibitionUuid;
    private String exhibitionName;
    private String name;
    private String description;
    private String thumbnailUrl;
    private String backgroundMusicUrl;
    private String backgroundMusicFileName;
    private Long backgroundMusicFileSize;
    private String displayTemplateKey;
    private BoothListingPriority listingPriority;
    private BoothStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String companyName;
    private String companyIndustry;
    private String companyEmail;
    private String companyPhone;
    private String companyAddress;
    private String companyWebsite;
    private String companyLogoUrl;
    private String companyDescription;
    private List<PanoramaResponseDTO> panoramas;
    private LocalDate boothReviewDeadline;
    private Boolean boothPreparationOpen;
    private Long daysUntilBoothDeadline;
    private Integer warningCount;
    private String warningReason;
    private Instant warnedAt;
    private String banReason;
    private Instant bannedAt;
}
