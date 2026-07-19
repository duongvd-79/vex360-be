package com.example.vex360.features.designrequest.events;

import java.util.UUID;

import com.example.vex360.shared.enums.DesignRequestStatus;

public record DesignRequestStatusChangedEvent(
        UUID requestId,
        UUID companyId,
        UUID designerId,
        UUID actorId,
        DesignRequestStatus previousStatus,
        DesignRequestStatus status) {
}
