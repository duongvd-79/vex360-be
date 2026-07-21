package com.example.vex360.features.designrequest.dtos.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.vex360.shared.enums.DesignRequestStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.enums.DesignRequestScope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesignRequestResponseDTO {
    private UUID id;
    private UUID boothId;
    private String boothName;
    private UUID companyId;
    private String companyName;
    private UUID exhibitionId;
    private String exhibitionName;
    private LocalDate exhibitionStartDate;
    private DesignRequestStatus status;
    private DesignRequestMode mode;
    private DesignRequestScope scope;
    private UUID assignedDesignerId;
    private String assignedDesignerName;
    private String note;
    private String reviewNote;
    private Integer reviewCount;
    private Integer requiredProductCount;
    private Integer optionalProductCount;
    private Integer remainingDesignActions;
    private DesignRequestCancellationStatus cancellationStatus;
    private String cancellationReason;
    private DesignDraftResponseDTO latestDraft;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
}
