package com.connecthub.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_recipient",       columnList = "recipient_id"),
        @Index(name = "idx_recipient_read",  columnList = "recipient_id, is_read"),
        @Index(name = "idx_room",            columnList = "room_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Cross-service references (no FK constraints) ──────
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "actor_id")
    private Long actorId;       // user who triggered the notification

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Enums ─────────────────────────────────────────────
    public enum NotificationType {
        NEW_MESSAGE,    // new message in a room where user is offline
        MENTION,        // @mention in a message
        ROOM_INVITE,    // invited to a room
        SYSTEM          // platform-wide broadcast
    }
}
