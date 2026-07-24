package com.example.vex360.features.chat.repositories;

import com.example.vex360.features.chat.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByRoomIdOrderBySentAtAsc(UUID roomId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.readAt = CURRENT_TIMESTAMP WHERE m.room.id = :roomId AND m.sender.id != :userId AND m.readAt IS NULL")
    void markMessagesAsRead(UUID roomId, UUID userId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.room.id = :roomId AND m.sender.id != :userId AND m.readAt IS NULL")
    long countUnreadByRoomIdAndUserId(UUID roomId, UUID userId);

    @Query("SELECT COUNT(m) FROM ChatMessage m "
            + "WHERE m.room.exhibitorUser.id = :exhibitorUserId AND m.sender.id != :exhibitorUserId AND m.readAt IS NULL")
    long countUnreadForExhibitor(UUID exhibitorUserId);

}
