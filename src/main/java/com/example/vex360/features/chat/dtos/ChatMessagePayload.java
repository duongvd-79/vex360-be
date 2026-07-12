package com.example.vex360.features.chat.dtos;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private LocalDateTime sentAt;
    private String type; // "CHAT_MESSAGE" | "READ_RECEIPT" | "TYPING"
}
