package com.example.vex360.features.designrequest.events;

import java.util.UUID;

public record DesignRequestMessageCreatedEvent(
        UUID requestId,
        UUID companyId,
        UUID designerId,
        UUID senderId,
        UUID messageId) {
}
