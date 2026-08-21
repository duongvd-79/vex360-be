package com.example.vex360.features.chat.dtos;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record MarkChatReadRequest(
        @NotNull(message = "Phòng chat không được để trống.")
        UUID roomId) {
}
