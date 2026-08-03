package com.example.vex360.features.chat.dtos;

import java.time.Instant;
import java.util.List;
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
public class ChatRoomResponse {

    private UUID roomId;
    private String exhibitorName;
    private String exhibitorAvatar;
    private boolean exhibitorOnline;
    private String visitorName;
    private String visitorAvatar;
    private boolean visitorOnline;
    private Instant lastMessageAt;
    private String lastMessagePreview;
    private List<ChatMessagePayload> messages;
    private int unreadCount;
}
