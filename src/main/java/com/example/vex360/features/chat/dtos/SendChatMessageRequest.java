package com.example.vex360.features.chat.dtos;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotNull(message = "Phòng chat không được để trống.")
        UUID roomId,

        @NotBlank(message = "Nội dung tin nhắn không được để trống.")
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự.")
        String content) {
}
