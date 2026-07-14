package com.example.vex360.features.chat.repositories;

import com.example.vex360.features.chat.entities.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
            Integer exhibitionId, UUID exhibitorUserId, UUID visitorUserId);

    List<ChatRoom> findByExhibitorUserIdOrderByLastMessageAtDesc(UUID exhibitorUserId);

    List<ChatRoom> findByVisitorUserIdOrderByLastMessageAtDesc(UUID visitorUserId);
}
