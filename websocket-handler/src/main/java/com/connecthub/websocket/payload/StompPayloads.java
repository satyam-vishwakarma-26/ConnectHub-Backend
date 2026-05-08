package com.connecthub.websocket.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

/**
 * All STOMP JSON frame schemas used by ConnectHub.
 *
 * Inbound  (/app/chat.*):  sent BY the client TO the server
 * Outbound (/topic/...):   broadcast BY the server TO subscribers
 *
 * Every outbound frame carries a "type" discriminator so the
 * frontend can switch on it in a single WebSocket handler.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StompPayloads {

    // ── Inbound: client sends a chat message ──────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChatMessagePayload {
        private Long   roomId;
        private Long   recipientId;
        private String content;
        private String type;           // TEXT | IMAGE | FILE
        private String mediaUrl;
        private Long   replyToId;
    }

    // ── Inbound: client sends typing indicator ────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TypingPayload {
        private Long    roomId;
        private Long    recipientId;
        private boolean isTyping;
    }

    // ── Inbound: client marks messages as read ────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReadReceiptPayload {
        private Long   roomId;
        private Long   upToMessageId;
        private String upToTime;       // ISO-8601 — messages sent up to this time
    }

    // ── Inbound: client sends emoji reaction ──────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReactionPayload {
        private Long   roomId;
        private Long   messageId;
        private String emoji;
    }

    // ── Inbound: client broadcasts a presence/status change ─
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PresenceUpdatePayload {
        private String status;          // ONLINE | AWAY | DND | INVISIBLE
        private String customMessage;   // optional status text
    }

    // ── Inbound: client requests an edit and broadcast ────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EditPayload {
        private Long   messageId;
        private String content;
        private Long   roomId;          // null for DM
        private Long   recipientId;     // null for group room
    }

    // ── Inbound: client requests a delete and broadcast ───
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeletePayload {
        private Long    messageId;
        private Long    roomId;          // null for DM
        private Long    recipientId;     // null for group room
        private Boolean isAdminDelete;   // true = use admin endpoint
    }

    // ── Inbound: client notifies delivery status change ───
    //    Sent by recipient after marking a message as READ via REST.
    //    Server broadcasts MESSAGE_STATUS to the sender's personal topic.
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusUpdatePayload {
        private Long   messageId;
        private Long   senderId;        // original message sender (to notify)
        private Long   roomId;          // null for DM
        private String status;          // DELIVERED | READ
    }

    // ── Outbound: new message broadcast ───────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChatMessageEvent {
        @Builder.Default private final String type = "CHAT_MESSAGE";
        private Long          messageId;
        private Long          roomId;
        private Long          recipientId;
        private Long          senderId;
        private String        senderName;
        private String        content;
        private String        messageType;
        private String        mediaUrl;
        private Long          replyToId;
        private String        deliveryStatus;
        private LocalDateTime sentAt;
    }

    // ── Outbound: typing indicator broadcast ──────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TypingIndicatorEvent {
        @Builder.Default private final String type = "TYPING_INDICATOR";
        private Long    senderId;
        private String  senderName;
        private Long    roomId;
        private boolean isTyping;
    }

    // ── Outbound: read receipt broadcast ──────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReadReceiptEvent {
        @Builder.Default private final String type = "READ_RECEIPT";
        private Long   readerId;
        private Long   roomId;
        private Long   upToMessageId;
        private String upToTime;
    }

    // ── Outbound: message edited ───────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageEditEvent {
        @Builder.Default private final String type = "MESSAGE_EDIT";
        private Long          editorId;
        private Long          messageId;
        private Long          roomId;
        private String        newContent;
        private LocalDateTime editedAt;
    }

    // ── Outbound: message deleted ──────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageDeleteEvent {
        @Builder.Default private final String type = "MESSAGE_DELETE";
        private Long deleterId;
        private Long messageId;
        private Long roomId;
    }

    // ── Outbound: emoji reaction ───────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReactionEvent {
        @Builder.Default private final String type = "REACTION";
        private Long   senderId;
        private Long   messageId;
        private Long   roomId;
        private String emoji;
    }

    // ── Outbound: user presence update ────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PresenceUpdateEvent {
        @Builder.Default private final String type = "PRESENCE_UPDATE";
        private Long          userId;
        private String        status;
        private String        customMessage;
        private LocalDateTime lastSeenAt;
    }

    // ── Outbound: delivery status change ──────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeliveryStatusEvent {
        @Builder.Default private final String type = "MESSAGE_STATUS";
        private Long   messageId;
        private Long   roomId;
        private String status;    // SENT | DELIVERED | READ
    }

    // ── Outbound: user joined room ─────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoomJoinEvent {
        @Builder.Default private final String type = "ROOM_JOIN";
        private Long   userId;
        private String username;
        private Long   roomId;
    }

    // ── Outbound: user left room ───────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoomLeaveEvent {
        @Builder.Default private final String type = "ROOM_LEAVE";
        private Long   userId;
        private String username;
        private Long   roomId;
    }

    // ── Outbound: personal alert (mention, invite, etc) ───
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PersonalAlertEvent {
        @Builder.Default private final String type = "PERSONAL_ALERT";
        private String alertType;   // MENTION | ROOM_INVITE | SYSTEM
        private String title;
        private String message;
        private Long   roomId;
        private Long   messageId;
        private Long   actorId;
    }
}
