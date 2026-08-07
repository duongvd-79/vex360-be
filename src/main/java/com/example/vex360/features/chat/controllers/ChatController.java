package com.example.vex360.features.chat.controllers;

import com.example.vex360.features.chat.dtos.ChatMessagePayload;
import com.example.vex360.features.chat.dtos.ChatRoomResponse;
import com.example.vex360.features.chat.dtos.GetOrCreateRoomRequest;
import com.example.vex360.features.chat.dtos.MarkChatReadRequest;
import com.example.vex360.features.chat.dtos.SendChatMessageRequest;
import com.example.vex360.features.auth.entities.CustomUserDetails;
import com.example.vex360.features.chat.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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

                return ResponseEntity.ok(chatService.getRoomsForUser(
                                userDetails.getUser().getId(),
                                userDetails.getUser().getRole()));
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
        public void sendMessage(@Valid @Payload SendChatMessageRequest payload, Principal principal) {

                UUID senderId = UUID.fromString(principal.getName());

                ChatMessagePayload saved = chatService.saveMessage(
                                payload.roomId(), senderId, payload.content());

                // Broadcast tin nhắn đến tất cả người đang subscribe phòng này
                messagingTemplate.convertAndSend(
                                "/topic/chat/" + payload.roomId(), saved);
        }

        // ── WebSocket: Đánh dấu đã đọc ──────────────────────────────
        @MessageMapping("/chat.read")
        public void markAsRead(@Valid @Payload MarkChatReadRequest payload, Principal principal) {

                UUID roomId = payload.roomId();
                UUID userId = UUID.fromString(principal.getName());
                chatService.markAsRead(roomId, userId);

                // Thông báo cho người kia biết tin đã được đọc
                ChatMessagePayload receipt = ChatMessagePayload.builder()
                                .roomId(roomId)
                                .senderId(userId)
                                .type("READ_RECEIPT")
                                .build();
                messagingTemplate.convertAndSend(
                                "/topic/chat/" + roomId, receipt);
        }
}
