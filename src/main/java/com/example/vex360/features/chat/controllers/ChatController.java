package com.example.vex360.features.chat.controllers;

import com.example.vex360.features.chat.dtos.ChatMessagePayload;
import com.example.vex360.features.chat.dtos.ChatRoomResponse;
import com.example.vex360.features.chat.dtos.GetOrCreateRoomRequest;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.chat.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── REST: Tạo hoặc lấy phòng chat ───────────────────────────
    @PostMapping("/api/v1/chats/rooms")
    @ResponseBody
    public ResponseEntity<ChatRoomResponse> getOrCreateRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GetOrCreateRoomRequest request) {

        return ResponseEntity.ok(
                chatService.getOrCreateRoom(userDetails.getUser().getId(), request));
    }

    // ── REST: Lấy danh sách phòng chat của user hiện tại ────────
    @GetMapping("/api/v1/chats/rooms")
    @ResponseBody
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(chatService.getRoomsForUser(userDetails.getUser()));
    }

    // ── REST: Lấy chi tiết 1 phòng chat kèm lịch sử tin nhắn ───
    @GetMapping("/api/v1/chats/rooms/{roomId}")
    @ResponseBody
    public ResponseEntity<ChatRoomResponse> getRoomById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID roomId) {

        return ResponseEntity.ok(
                chatService.getRoomById(roomId, userDetails.getUser().getId()));
    }

    // ── WebSocket: Gửi tin nhắn ──────────────────────────────────
    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessagePayload payload, Principal principal) {

        UUID senderId = UUID.fromString(principal.getName());

        ChatMessagePayload saved = chatService.saveMessage(
                payload.getRoomId(), senderId, payload.getContent());

        // Broadcast tin nhắn đến tất cả người đang subscribe phòng này
        messagingTemplate.convertAndSend(
                "/topic/chat/" + payload.getRoomId(), saved);
    }

    // ── WebSocket: Đánh dấu đã đọc ──────────────────────────────
    @MessageMapping("/chat.read")
    public void markAsRead(ChatMessagePayload payload, Principal principal) {

        UUID userId = UUID.fromString(principal.getName());
        chatService.markAsRead(payload.getRoomId(), userId);

        // Thông báo cho người kia biết tin đã được đọc
        payload.setType("READ_RECEIPT");
        messagingTemplate.convertAndSend(
                "/topic/chat/" + payload.getRoomId(), payload);
    }
}
