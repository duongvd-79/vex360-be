package com.example.vex360.features.chat.dtos;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessagePayload {

    private UUID messageId;
    private UUID roomId;
    private UUID senderId;
    private String senderName;
    private String senderAvatar;
    private String senderRole;
    private String content;
    private Instant sentAt;
    private String type; // "CHAT_MESSAGE" | "READ_RECEIPT" | "TYPING"
}
