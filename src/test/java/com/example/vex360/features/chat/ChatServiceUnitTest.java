package com.example.vex360.features.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.chat.dtos.ChatMessagePayload;
import com.example.vex360.features.chat.dtos.ChatRoomResponse;
import com.example.vex360.features.chat.dtos.GetOrCreateRoomRequest;
import com.example.vex360.features.chat.entities.ChatMessage;
import com.example.vex360.features.chat.entities.ChatRoom;
import com.example.vex360.features.chat.repositories.ChatMessageRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.chat.services.ChatService;
import com.example.vex360.features.chat.services.PresenceService;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.shared.enums.Role;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

/**
 * Test case design (UTCID matrix theo mẫu FSOFT) nằm ở
 * {@code ChatService_TestCaseDesign.md} cùng thư mục.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceUnitTest {

    @Mock
    ChatRoomRepository chatRoomRepository;
    @Mock
    ChatMessageRepository chatMessageRepository;
    @Mock
    UserService userService;
    @Mock
    ExhibitionService exhibitionService;
    @Mock
    PresenceService presenceService;
    @Mock
    BoothDesignService boothDesignService;
    @InjectMocks
    ChatService chatService;

    // ================= getOrCreateRoom =================

    @Test
    void getOrCreateRoom_UTCID01_NoExistingRoom_CreatesNewRoom() {
        UUID visitorId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        Integer exhibitionId = 10;
        UUID exhibitionUuid = UUID.randomUUID();
        GetOrCreateRoomRequest request = new GetOrCreateRoomRequest();
        request.setExhibitionId(exhibitionUuid);
        request.setExhibitorUserId(exhibitorId);

        Exhibition exhibition = Exhibition.builder().id(exhibitionId).uuid(exhibitionUuid).build();
        User exhibitor = User.builder().id(exhibitorId).build();
        User visitor = User.builder().id(visitorId).build();
        ChatRoom room = ChatRoom.builder().id(UUID.randomUUID()).exhibition(exhibition)
                .exhibitorUser(exhibitor).visitorUser(visitor).build();

        when(exhibitionService.findExhibitionEntityByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(userService.getUserEntityById(exhibitorId)).thenReturn(exhibitor);
        when(userService.getUserEntityById(visitorId)).thenReturn(visitor);
        when(chatRoomRepository.findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
                exhibitionId, exhibitorId, visitorId)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(room);
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(room.getId())).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateRoom(visitorId, request);

        assertEquals(room.getId(), response.getRoomId());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void getOrCreateRoom_UTCID02_RoomAlreadyExists_ReturnsExistingRoomWithoutCreatingNew() {
        UUID visitorId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        Integer exhibitionId = 10;
        UUID exhibitionUuid = UUID.randomUUID();
        GetOrCreateRoomRequest request = new GetOrCreateRoomRequest();
        request.setExhibitionId(exhibitionUuid);
        request.setExhibitorUserId(exhibitorId);

        Exhibition exhibition = Exhibition.builder().id(exhibitionId).uuid(exhibitionUuid).build();
        User exhibitor = User.builder().id(exhibitorId).build();
        User visitor = User.builder().id(visitorId).build();
        ChatRoom existingRoom = ChatRoom.builder().id(UUID.randomUUID()).exhibition(exhibition)
                .exhibitorUser(exhibitor).visitorUser(visitor)
                .lastMessagePreview("Chào bạn!").build();

        when(exhibitionService.findExhibitionEntityByUuid(exhibitionUuid)).thenReturn(exhibition);
        when(userService.getUserEntityById(exhibitorId)).thenReturn(exhibitor);
        when(userService.getUserEntityById(visitorId)).thenReturn(visitor);
        when(chatRoomRepository.findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
                exhibitionId, exhibitorId, visitorId)).thenReturn(Optional.of(existingRoom));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(existingRoom.getId())).thenReturn(List.of());

        ChatRoomResponse response = chatService.getOrCreateRoom(visitorId, request);

        assertEquals(existingRoom.getId(), response.getRoomId());
        assertEquals("Chào bạn!", response.getLastMessagePreview());
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void getOrCreateRoom_UTCID03_ExhibitionNotFound_ThrowsException() {
        UUID visitorId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID exhibitionUuid = UUID.randomUUID();
        GetOrCreateRoomRequest request = new GetOrCreateRoomRequest();
        request.setExhibitionId(exhibitionUuid);
        request.setExhibitorUserId(exhibitorId);

        when(exhibitionService.findExhibitionEntityByUuid(exhibitionUuid))
                .thenThrow(new AppException(ErrorCode.EXHIBITION_NOT_FOUND));

        AppException exception = assertThrows(AppException.class,
                () -> chatService.getOrCreateRoom(visitorId, request));

        assertSame(ErrorCode.EXHIBITION_NOT_FOUND, exception.getErrorCode());
        verify(userService, never()).getUserEntityById(any());
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    // ================= saveMessage =================

    @Test
    void saveMessage_UTCID01_SenderIsExhibitor_ShortContent_SetsExhibitorRoleAndFullPreview() {
        UUID roomId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        User exhibitor = User.builder().id(exhibitorId).fullName("Trần Quang Huy").build();
        User visitor = User.builder().id(visitorId).fullName("Hoàng Khách Tham Quan").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitor).visitorUser(visitor).build();
        String content = "Chào bạn, sản phẩm còn hàng nhé!";

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserEntityById(exhibitorId)).thenReturn(exhibitor);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        ChatMessage savedMessage = ChatMessage.builder().id(UUID.randomUUID()).room(room).sender(exhibitor)
                .senderRole("EXHIBITOR").content(content).sentAt(Instant.now()).build();
        when(chatMessageRepository.save(messageCaptor.capture())).thenReturn(savedMessage);

        ChatMessagePayload payload = chatService.saveMessage(roomId, exhibitorId, content);

        assertEquals("EXHIBITOR", messageCaptor.getValue().getSenderRole());
        assertEquals("EXHIBITOR", payload.getSenderRole());
        assertEquals(content, payload.getContent());

        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(roomCaptor.capture());
        assertEquals(content, roomCaptor.getValue().getLastMessagePreview());
    }

    @Test
    void saveMessage_UTCID02_SenderIsVisitor_LongContent_TruncatesPreview() {
        UUID roomId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        User exhibitor = User.builder().id(exhibitorId).fullName("Trần Quang Huy").build();
        User visitor = User.builder().id(visitorId).fullName("Hoàng Khách Tham Quan").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitor).visitorUser(visitor).build();
        // 60 ký tự -> vượt ngưỡng 50 ký tự cắt preview
        String longContent = "A".repeat(60);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserEntityById(visitorId)).thenReturn(visitor);

        ChatMessage savedMessage = ChatMessage.builder().id(UUID.randomUUID()).room(room).sender(visitor)
                .senderRole("VISITOR").content(longContent).sentAt(Instant.now()).build();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ChatMessagePayload payload = chatService.saveMessage(roomId, visitorId, longContent);

        assertEquals("VISITOR", payload.getSenderRole());

        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(roomCaptor.capture());
        assertEquals(longContent.substring(0, 50) + "...", roomCaptor.getValue().getLastMessagePreview());
    }

    @Test
    void saveMessage_UTCID03_RoomNotFound_ThrowsException() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> chatService.saveMessage(roomId, senderId, "Xin chào"));

        assertSame(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    // ================= markAsRead =================

    @Test
    void markAsRead_UTCID01_DelegatesToRepositoryWithCorrectParams() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        chatService.markAsRead(roomId, userId);

        verify(chatMessageRepository).markMessagesAsRead(roomId, userId);
    }

    // ================= getRoomById =================

    @Test
    void getRoomById_UTCID01_RequesterIsExhibitor_ReturnsRoom() {
        UUID roomId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        User exhibitor = User.builder().id(exhibitorId).fullName("Trần Quang Huy").build();
        User visitor = User.builder().id(visitorId).fullName("Hoàng Khách Tham Quan").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitor).visitorUser(visitor).build();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getRoomById(roomId, exhibitorId);

        assertEquals(roomId, response.getRoomId());
        assertEquals(exhibitor.getFullName(), response.getExhibitorName());
        assertEquals(visitor.getFullName(), response.getVisitorName());
    }

    @Test
    void getRoomById_UTCID02_RequesterIsVisitor_ReturnsRoom() {
        UUID roomId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        User exhibitor = User.builder().id(exhibitorId).fullName("Trần Quang Huy").build();
        User visitor = User.builder().id(visitorId).fullName("Hoàng Khách Tham Quan").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitor).visitorUser(visitor).build();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(roomId)).thenReturn(List.of());

        ChatRoomResponse response = chatService.getRoomById(roomId, visitorId);

        assertEquals(roomId, response.getRoomId());
    }

    @Test
    void getRoomById_UTCID03_RequesterNotInRoom_ThrowsUnauthorized() {
        UUID roomId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        User exhibitor = User.builder().id(exhibitorId).build();
        User visitor = User.builder().id(visitorId).build();
        ChatRoom room = ChatRoom.builder().id(roomId).exhibitorUser(exhibitor).visitorUser(visitor).build();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        AppException exception = assertThrows(AppException.class,
                () -> chatService.getRoomById(roomId, strangerId));

        assertSame(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(chatMessageRepository, never()).findByRoomIdOrderBySentAtAsc(any());
    }

    @Test
    void getRoomById_UTCID04_RoomNotFound_ThrowsException() {
        UUID roomId = UUID.randomUUID();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> chatService.getRoomById(roomId, UUID.randomUUID()));

        assertSame(ErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
    }

    // ================= getRoomsForUser =================

    @Test
    void getRoomsForUser_UTCID01_ExhibitorRole_QueriesByExhibitorAndMapsUnreadCount() {
        UUID exhibitorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        User exhibitorUser = User.builder().id(exhibitorId).role(Role.EXHIBITOR).fullName("Trần Quang Huy").build();
        User visitor = User.builder().id(UUID.randomUUID()).fullName("Hoàng Khách Tham Quan").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitorUser).visitorUser(visitor).build();

        when(chatRoomRepository.findByExhibitorUserIdOrderByLastMessageAtDesc(exhibitorId))
                .thenReturn(List.of(room));
        when(chatMessageRepository.countUnreadByRoomIdAndUserId(roomId, exhibitorId)).thenReturn(3L);

        List<ChatRoomResponse> result = chatService.getRoomsForUser(exhibitorId, Role.EXHIBITOR);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getUnreadCount());
        verify(chatRoomRepository).findByExhibitorUserIdOrderByLastMessageAtDesc(exhibitorId);
        verify(chatRoomRepository, never()).findByVisitorUserIdOrderByLastMessageAtDesc(any());
    }

    @Test
    void getRoomsForUser_UTCID02_VisitorRole_QueriesByVisitor() {
        UUID visitorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        User visitorUser = User.builder().id(visitorId).role(Role.VISITOR).fullName("Hoàng Khách Tham Quan").build();
        User exhibitor = User.builder().id(UUID.randomUUID()).fullName("Trần Quang Huy").build();
        ChatRoom room = ChatRoom.builder().id(roomId)
                .exhibition(Exhibition.builder().id(1).name("Demo").build())
                .exhibitorUser(exhibitor).visitorUser(visitorUser).build();

        when(chatRoomRepository.findByVisitorUserIdOrderByLastMessageAtDesc(visitorId))
                .thenReturn(List.of(room));
        when(chatMessageRepository.countUnreadByRoomIdAndUserId(roomId, visitorId)).thenReturn(0L);

        List<ChatRoomResponse> result = chatService.getRoomsForUser(visitorId, Role.VISITOR);

        assertEquals(1, result.size());
        verify(chatRoomRepository).findByVisitorUserIdOrderByLastMessageAtDesc(visitorId);
        verify(chatRoomRepository, never()).findByExhibitorUserIdOrderByLastMessageAtDesc(any());
    }

    @Test
    void getRoomsForUser_UTCID03_NoRooms_ReturnsEmptyList() {
        UUID exhibitorId = UUID.randomUUID();
        when(chatRoomRepository.findByExhibitorUserIdOrderByLastMessageAtDesc(exhibitorId))
                .thenReturn(List.of());

        List<ChatRoomResponse> result = chatService.getRoomsForUser(exhibitorId, Role.EXHIBITOR);

        assertTrue(result.isEmpty());
    }
}
