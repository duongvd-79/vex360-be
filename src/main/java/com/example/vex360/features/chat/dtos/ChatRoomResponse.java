package com.example.vex360.features.chat.dtos;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomResponse {

    private UUID roomId;
    private String exhibitorName;
    private String exhibitorAvatar;
    private String visitorName;
    private String visitorAvatar;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private List<ChatMessagePayload> messages;
}
