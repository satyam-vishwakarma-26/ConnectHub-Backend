package com.connecthub.message.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a single chat message within a room.
 *
 * <p>Lifecycle for deliveryStatus:
 * <pre>
 *   SENT (persisted)
 *     → DELIVERED (websocket-handler confirms recipient session is active)
 *     → READ      (READ_RECEIPT STOMP event received)
 * </pre>
 *
 * <p>Soft-delete: isDeleted=true; content is blanked. A MESSAGE_DELETE STOMP
 * event is broadcast by websocket-handler so all clients remove it from their UI.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_room_id",    columnList = "room_id"),
    @Index(name = "idx_messages_recipient_id", columnList = "recipient_id"),
        @Index(name = "idx_messages_sender_id",  columnList = "sender_id"),
        @Index(name = "idx_messages_sent_at",    columnList = "sent_at"),
        @Index(name = "idx_messages_room_sent",  columnList = "room_id, sent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Room context (references room-service — no FK across services) ──
    @Column(name = "room_id")
    private Long roomId;

    // ── Direct-message context (1:1 DM) ─────────────────
    @Column(name = "recipient_id")
    private Long recipientId;

    // ── Sender (references auth-service — no FK across services) ──
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    // ── Media (set when type = IMAGE or FILE) ──────────────
    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    @Column(name = "media_filename", length = 300)
    private String mediaFilename;

    @Column(name = "media_size_kb")
    private Long mediaSizeKb;

    // ── Threading ──────────────────────────────────────────
    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    // ── State ──────────────────────────────────────────────
    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 15)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    // ── Timestamps ─────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @UpdateTimestamp
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    // ── Pinned support (Room Admin feature) ───────────────
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    // ── Enums ──────────────────────────────────────────────

    public enum MessageType {
        TEXT,       // Plain text message
        IMAGE,      // Inline image with mediaUrl + thumbnail
        FILE,       // Document / file attachment
        REACTION,   // Emoji reaction (stored for history; live via STOMP)
        SYSTEM      // System-generated event (join / leave / room rename)
    }

    public enum DeliveryStatus {
        SENT,       // Persisted in DB; sender confirmed
        DELIVERED,  // Recipient WebSocket session is active
        READ        // READ_RECEIPT event confirmed by recipient
    }
}
