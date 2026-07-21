package com.example.vex360.features.designrequest.events;

import java.util.UUID;

public record DesignRequestRevisionPromotedEvent(UUID requestId, UUID designerId) {
}
