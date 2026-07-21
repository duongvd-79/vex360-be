package com.example.vex360.features.designrequest.events;

import java.util.UUID;

import com.example.vex360.features.designrequest.enums.DesignRequestCancellationStatus;

public record DesignRequestCancellationChangedEvent(
        UUID requestId,
        UUID companyId,
        UUID designerId,
        UUID actorId,
        DesignRequestCancellationStatus status) {
}
