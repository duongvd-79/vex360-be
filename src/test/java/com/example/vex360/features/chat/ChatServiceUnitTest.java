package com.example.vex360.features.chat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.chat.dtos.GetOrCreateRoomRequest;
import com.example.vex360.features.chat.entities.ChatRoom;
import com.example.vex360.features.chat.repositories.ChatMessageRepository;
import com.example.vex360.features.chat.repositories.ChatRoomRepository;
import com.example.vex360.features.chat.services.ChatService;
import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.user.services.UserService;

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
    @InjectMocks
    ChatService chatService;

    @Test
    void getsRelatedEntitiesThroughTheirFeatureServices() {
        UUID visitorId = UUID.randomUUID();
        UUID exhibitorId = UUID.randomUUID();
        Integer exhibitionId = 10;
        GetOrCreateRoomRequest request = new GetOrCreateRoomRequest();
        request.setExhibitionId(exhibitionId);
        request.setExhibitorUserId(exhibitorId);

        Exhibition exhibition = Exhibition.builder().id(exhibitionId).build();
        User exhibitor = User.builder().id(exhibitorId).build();
        User visitor = User.builder().id(visitorId).build();
        ChatRoom room = ChatRoom.builder().id(UUID.randomUUID()).exhibition(exhibition)
                .exhibitorUser(exhibitor).visitorUser(visitor).build();

        when(exhibitionService.getExhibitionEntityById(exhibitionId)).thenReturn(exhibition);
        when(userService.getUserEntityById(exhibitorId)).thenReturn(exhibitor);
        when(userService.getUserEntityById(visitorId)).thenReturn(visitor);
        when(chatRoomRepository.findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
                exhibitionId, exhibitorId, visitorId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByRoomIdOrderBySentAtAsc(room.getId())).thenReturn(List.of());

        chatService.getOrCreateRoom(visitorId, request);

        verify(exhibitionService).getExhibitionEntityById(exhibitionId);
        verify(userService).getUserEntityById(exhibitorId);
        verify(userService).getUserEntityById(visitorId);
    }
}
