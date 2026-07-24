package com.example.vex360.features.chat.services;

import com.example.vex360.features.chat.dtos.ChatMessagePayload;
import com.example.vex360.features.chat.dtos.ChatRoomResponse;
import com.example.vex360.features.chat.dtos.GetOrCreateRoomRequest;
import com.example.vex360.features.chat.entities.ChatMessage;
import com.example.vex360.features.chat.entities.ChatRoom;
import com.example.vex360.features.chat.repositories.ChatMessageRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.repositories.ExhibitionRepository;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final ExhibitionRepository exhibitionRepository;

    // ── 1. Tạo hoặc lấy phòng chat ──────────────────────────────
    @Transactional
    public ChatRoomResponse getOrCreateRoom(UUID visitorId, GetOrCreateRoomRequest request) {

        Exhibition exhibition = exhibitionRepository.findByUuid(request.getExhibitionId())
                .orElseThrow(() -> new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        var exhibitorUser = userService.getUserEntityById(request.getExhibitorUserId());

        var visitorUser = userService.getUserEntityById(visitorId);

        // Tìm phòng đã có, nếu chưa có thì tạo mới
        ChatRoom room = chatRoomRepository
                .findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
                        exhibition.getId(),
                        exhibitorUser.getId(),
                        visitorUser.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .exhibition(exhibition)
                                .exhibitorUser(exhibitorUser)
                                .visitorUser(visitorUser)
                                .build()));

        // Lấy lịch sử tin nhắn
        List<ChatMessagePayload> messages = chatMessageRepository
                .findByRoomIdOrderBySentAtAsc(room.getId())
                .stream()
                .map(this::toPayload)
                .toList();

        return ChatRoomResponse.builder()
                .roomId(room.getId())
                .exhibitorName(exhibitorUser.getFullName())
                .exhibitorAvatar(exhibitorUser.getAvatarUrl())
                .visitorName(visitorUser.getFullName())
                .visitorAvatar(visitorUser.getAvatarUrl())
                .lastMessageAt(room.getLastMessageAt())
                .lastMessagePreview(room.getLastMessagePreview())
                .messages(messages)
                .build();
    }

    // ── 2. Lưu tin nhắn mới ─────────────────────────────────────
    @Transactional
    public ChatMessagePayload saveMessage(UUID roomId, UUID senderId, String content) {

        var room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        var sender = userService.getUserEntityById(senderId);

        // Xác định role của người gửi
        String senderRole = sender.getId().equals(room.getExhibitorUser().getId())
                ? "EXHIBITOR"
                : "VISITOR";

        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .room(room)
                        .sender(sender)
                        .senderRole(senderRole)
                        .content(content)
                        .build());

        // Cập nhật preview ở chat_rooms
        room.setLastMessageAt(LocalDateTime.now());
        room.setLastMessagePreview(content.length() > 50
                ? content.substring(0, 50) + "..."
                : content);
        chatRoomRepository.save(room);

        return toPayload(message);
    }

    // ── 3. Đánh dấu đã đọc ──────────────────────────────────────
    @Transactional
    public void markAsRead(UUID roomId, UUID userId) {
        chatMessageRepository.markMessagesAsRead(roomId, userId);
    }

    // ── 4. Chi tiết 1 phòng chat (kèm lịch sử tin nhắn) ─────────
    public ChatRoomResponse getRoomById(UUID roomId, UUID userId) {
        var room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!room.getExhibitorUser().getId().equals(userId)
                && !room.getVisitorUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<ChatMessagePayload> messages = chatMessageRepository
                .findByRoomIdOrderBySentAtAsc(room.getId())
                .stream()
                .map(this::toPayload)
                .toList();

        return ChatRoomResponse.builder()
                .roomId(room.getId())
                .exhibitorName(room.getExhibitorUser().getFullName())
                .exhibitorAvatar(room.getExhibitorUser().getAvatarUrl())
                .visitorName(room.getVisitorUser().getFullName())
                .visitorAvatar(room.getVisitorUser().getAvatarUrl())
                .lastMessageAt(room.getLastMessageAt())
                .lastMessagePreview(room.getLastMessagePreview())
                .messages(messages)
                .build();
    }

    // ── 5. Danh sách phòng chat của 1 user ───────────────────────
    public List<ChatRoomResponse> getRoomsForUser(User user) {
        String role = user.getRole().name();

        List<ChatRoom> rooms = role.equals("EXHIBITOR")
                ? chatRoomRepository.findByExhibitorUserIdOrderByLastMessageAtDesc(user.getId())
                : chatRoomRepository.findByVisitorUserIdOrderByLastMessageAtDesc(user.getId());

        return rooms.stream().map(room -> ChatRoomResponse.builder()
                .roomId(room.getId())
                .exhibitorName(room.getExhibitorUser().getFullName())
                .exhibitorAvatar(room.getExhibitorUser().getAvatarUrl())
                .visitorName(room.getVisitorUser().getFullName())
                .visitorAvatar(room.getVisitorUser().getAvatarUrl())
                .lastMessageAt(room.getLastMessageAt())
                .lastMessagePreview(room.getLastMessagePreview())
                .unreadCount((int) chatMessageRepository.countUnreadByRoomIdAndUserId(room.getId(),
                        user.getId()))
                .messages(List.of())
                .build()).toList();
    }

    // ── Helper: Entity → DTO ─────────────────────────────────────
    private ChatMessagePayload toPayload(ChatMessage msg) {
        return ChatMessagePayload.builder()
                .messageId(msg.getId())
                .roomId(msg.getRoom().getId())
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getFullName())
                .senderAvatar(msg.getSender().getAvatarUrl())
                .senderRole(msg.getSenderRole())
                .content(msg.getContent())
                .sentAt(msg.getSentAt())
                .type("CHAT_MESSAGE")
                .build();
    }
}
