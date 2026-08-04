package com.example.vex360.features.chat.entities;

import com.example.vex360.features.exhibition.entities.Exhibition;
import com.example.vex360.features.user.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibitor_user_id", nullable = false)
    private User exhibitorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitor_user_id", nullable = false)
    private User visitorUser;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        // Phòng mới chưa có tin nhắn nào — coi thời điểm tạo phòng là "hoạt động gần
        // nhất"
        // để cột này không bao giờ NULL (một số schema cũ ràng buộc NOT NULL ở cột này)
        // và để phòng mới sắp xếp đúng thứ tự trong danh sách "gần đây".
        if (this.lastMessageAt == null) {
            this.lastMessageAt = this.createdAt;
        }
    }
}
