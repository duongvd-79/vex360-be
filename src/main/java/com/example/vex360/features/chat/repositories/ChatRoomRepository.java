package com.example.vex360.features.chat.repositories;

import com.example.vex360.features.chat.entities.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByExhibitionIdAndExhibitorUserIdAndVisitorUserId(
            Integer exhibitionId, UUID exhibitorUserId, UUID visitorUserId);

    @EntityGraph(attributePaths = { "exhibitorUser", "visitorUser" })
    List<ChatRoom> findByExhibitorUserIdOrderByLastMessageAtDesc(UUID exhibitorUserId);

    @EntityGraph(attributePaths = { "exhibitorUser", "visitorUser" })
    List<ChatRoom> findByVisitorUserIdOrderByLastMessageAtDesc(UUID visitorUserId);

    @Query(value = """
            SELECT DATE(created_at) AS day, COUNT(*) AS chats
            FROM chat_rooms
            WHERE exhibition_id = :exhibitionId
              AND created_at BETWEEN :start AND :end
            GROUP BY DATE(created_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countDailyChats(
            @Param("exhibitionId") Integer exhibitionId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
