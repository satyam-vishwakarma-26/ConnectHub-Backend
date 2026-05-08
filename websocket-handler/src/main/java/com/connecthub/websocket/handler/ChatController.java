package com.connecthub.websocket.handler;

import com.connecthub.websocket.payload.StompPayloads.*;
import com.connecthub.websocket.service.DownstreamService;
import com.connecthub.websocket.service.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ChatController — the Java equivalent of Socket.io's event emitter.
 *
 * Inbound  STOMP destinations:
 *   /app/chat.send    → handleChatMessage()
 *   /app/chat.typing  → handleTyping()
 *   /app/chat.read    → handleReadReceipt()
 *   /app/chat.react   → handleReaction()
 *   /app/chat.ping    → handlePing()
 *   /app/chat.status  → handleStatusUpdate()
 *
 * Outbound STOMP destinations:
 *   /topic/room/{roomId}   → all messages, edits, deletes, reactions, typing
 *   /topic/presence        → online/offline updates
 *   /user/{userId}/queue/  → personal alerts (mentions, invites)
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messaging;
    private final SessionRegistry       sessions;
    private final DownstreamService     downstream;

    // ── WebSocket lifecycle events ────────────────────────

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(event.getMessage());
        }
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();

        if (attrs == null || !attrs.containsKey("userId")) {
            log.warn("[WS] CONNECT session={} — no userId in attributes", sessionId);
            return;
        }

        Long   userId   = (Long)   attrs.get("userId");
        String email    = (String) attrs.get("email");
        String username = email != null ? email.split("@")[0] : "user-" + userId;
        String ip       = "unknown"; // available from handshake if needed

        sessions.register(sessionId, userId, username);

        // Notify presence-service (best-effort, fire-and-forget)
        downstream.setUserOnline(userId, sessionId, "WEB", ip);

        // Note: PRESENCE_UPDATE(ONLINE) broadcast is deferred to handleSubscribe()
        // when the user subscribes to /topic/presence. Broadcasting here is too early
        // because other clients haven't subscribed yet and will miss the event.

        log.info("[WS] CONNECTED userId={} session={}", userId, sessionId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Long userId = sessions.getUserId(sessionId);
        sessions.deregister(sessionId);
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String authToken = attrs != null ? (String) attrs.get("token") : null;

        if (userId != null) {
            downstream.setUserOffline(userId, sessionId, authToken);

            // Broadcast presence update
            PresenceUpdateEvent presenceEvent = PresenceUpdateEvent.builder()
                    .userId(userId)
                    .status("INVISIBLE")
                    .lastSeenAt(LocalDateTime.now())
                    .build();
            messaging.convertAndSend("/topic/presence", presenceEvent);

            log.info("[WS] DISCONNECTED userId={} session={}", userId, sessionId);
        }
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String dest      = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        Long   userId    = sessions.getUserId(sessionId);

        if (dest != null && dest.startsWith("/topic/room/")) {
            String roomIdStr = dest.replace("/topic/room/", "");
            try {
                Long roomId = Long.parseLong(roomIdStr);
                // Mark all existing messages in this room as DELIVERED
                if (userId != null) {
                    downstream.updateDeliveryStatus(null, userId, "DELIVERED");
                }
                log.debug("[WS] SUBSCRIBED userId={} → {}", userId, dest);
            } catch (NumberFormatException ignored) {}
        }

        // When a user subscribes to /topic/presence, two things happen:
        //
        // 1. Broadcast THIS user's ONLINE status to /topic/presence
        //    → other already-subscribed users learn this user is online.
        //
        // 2. Send ALL currently online users' statuses to THIS user's
        //    personal topic → this user catches up on anyone who
        //    connected before their subscription was ready.
        if ("/topic/presence".equals(dest) && userId != null) {
            // (1) Tell everyone about this user
            PresenceUpdateEvent myEvent = PresenceUpdateEvent.builder()
                    .userId(userId)
                    .status("ONLINE")
                    .build();
            messaging.convertAndSend("/topic/presence", myEvent);

            // (2) Tell this user about everyone else who is already online
            for (Long onlineUserId : sessions.getAllOnlineUserIds()) {
                if (!onlineUserId.equals(userId)) {
                    PresenceUpdateEvent peerEvent = PresenceUpdateEvent.builder()
                            .userId(onlineUserId)
                            .status("ONLINE")
                            .build();
                    messaging.convertAndSend("/topic/user/" + userId, peerEvent);
                }
            }

            log.debug("[WS] PRESENCE subscribed userId={}, broadcast + {} online peers",
                      userId, sessions.getAllOnlineUserIds().size() - 1);
        }
    }

    // ── Inbound: send message ─────────────────────────────

    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload ChatMessagePayload payload,
                                   SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long   senderId  = resolveUserId(sessionId, headerAccessor);
        String senderName = resolveUsername(sessionId, headerAccessor, senderId);
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String authToken = attrs != null ? (String) attrs.get("token") : null;

        if (senderId == null) {
            log.warn("[WS] chat.send — unknown session {}", sessionId);
            return;
        }

        if (authToken == null || authToken.isBlank()) {
            log.warn("[WS] chat.send — missing auth token for session {}", sessionId);
            return;
        }

        if (payload.getRoomId() != null) {
            log.debug("[WS] chat.send roomId={} sender={}", payload.getRoomId(), senderId);

            // 1. Persist via message-service (async, non-blocking)
            downstream.persistMessage(
                    senderId,
                    payload.getRoomId(),
                    payload.getContent(),
                    payload.getType(),
                    payload.getMediaUrl(),
                    payload.getReplyToId(),
                    authToken
            ).subscribe(savedMsg -> {
                Map<?, ?> data = extractData(savedMsg);
                Long messageId = extractLong(data, "id");

                ChatMessageEvent event = ChatMessageEvent.builder()
                        .messageId(messageId)
                        .roomId(payload.getRoomId())
                        .senderId(senderId)
                        .senderName(senderName)
                        .content(payload.getContent())
                        .messageType(payload.getType() != null ? payload.getType() : "TEXT")
                        .mediaUrl(payload.getMediaUrl())
                        .replyToId(payload.getReplyToId())
                        .deliveryStatus("SENT")
                        .sentAt(LocalDateTime.now())
                        .build();

                // 2. Broadcast to room subscribers
                messaging.convertAndSend(
                        "/topic/room/" + payload.getRoomId(), event);

                // 3. Send notifications to room members (except sender)
                notifyRoomMembers(payload.getRoomId(), senderId, senderName, messageId,
                    payload.getContent(), authToken);

                // 4. Check for @mentions and send personal alerts
                if (payload.getContent() != null && payload.getContent().contains("@")) {
                    handleMentions(payload.getContent(), senderId, senderName,
                            payload.getRoomId(), messageId);
                }
            });
            return;
        }

        if (payload.getRecipientId() == null) {
            log.warn("[WS] chat.send — either roomId or recipientId is required");
            return;
        }

        log.debug("[WS] direct.send recipientId={} sender={}", payload.getRecipientId(), senderId);

        downstream.persistDirectMessage(
                senderId,
                payload.getRecipientId(),
                payload.getContent(),
                payload.getType(),
                payload.getMediaUrl(),
                payload.getReplyToId(),
                authToken
        ).subscribe(savedMsg -> {
            Map<?, ?> data = extractData(savedMsg);
            Long messageId = extractLong(data, "id");

            ChatMessageEvent event = ChatMessageEvent.builder()
                    .messageId(messageId)
                    .roomId(null)
                    .recipientId(payload.getRecipientId())
                    .senderId(senderId)
                    .senderName(senderName)
                    .content(payload.getContent())
                    .messageType(payload.getType() != null ? payload.getType() : "TEXT")
                    .mediaUrl(payload.getMediaUrl())
                    .replyToId(payload.getReplyToId())
                    .deliveryStatus("SENT")
                    .sentAt(LocalDateTime.now())
                    .build();

            // Fan-out to both sender and recipient personal channels for realtime DM updates.
            messaging.convertAndSend("/topic/user/" + senderId, event);
            if (!senderId.equals(payload.getRecipientId())) {
                messaging.convertAndSend("/topic/user/" + payload.getRecipientId(), event);
            }

            // Send DM notification to recipient
            downstream.sendNotification(
                    payload.getRecipientId(),
                    senderId,
                    "NEW_MESSAGE",
                    senderName + " sent you a message",
                    payload.getContent() != null && payload.getContent().length() > 100
                            ? payload.getContent().substring(0, 100) + "..."
                            : payload.getContent(),
                    null,
                    messageId,
                    authToken
            );
        });
    }

    // ── Inbound: typing indicator ─────────────────────────

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingPayload payload,
                              SimpMessageHeaderAccessor headerAccessor) {
        String sessionId  = headerAccessor.getSessionId();
        Long   senderId   = resolveUserId(sessionId, headerAccessor);
        String senderName = resolveUsername(sessionId, headerAccessor, senderId);

        if (senderId == null) return;

        // Typing events are NOT persisted — broadcast only
        TypingIndicatorEvent event = TypingIndicatorEvent.builder()
                .senderId(senderId)
                .senderName(senderName)
                .roomId(payload.getRoomId())
                .isTyping(payload.isTyping())
                .build();

        if (payload.getRoomId() != null) {
            messaging.convertAndSend("/topic/room/" + payload.getRoomId(), event);
            return;
        }

        if (payload.getRecipientId() != null) {
            messaging.convertAndSend("/topic/user/" + senderId, event);
            if (!senderId.equals(payload.getRecipientId())) {
                messaging.convertAndSend("/topic/user/" + payload.getRecipientId(), event);
            }
        }
    }

    // ── Inbound: read receipt ─────────────────────────────

    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload ReadReceiptPayload payload,
                                   SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long   readerId  = resolveUserId(sessionId, headerAccessor);

        if (readerId == null) return;

        // 1. Update delivery status in message-service
        String upToTime = payload.getUpToTime() != null
                ? payload.getUpToTime()
                : LocalDateTime.now().toString();

        downstream.markRoomRead(payload.getRoomId(), readerId, upToTime);

        // 2. Broadcast READ_RECEIPT event to all room subscribers
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .readerId(readerId)
                .roomId(payload.getRoomId())
                .upToMessageId(payload.getUpToMessageId())
                .upToTime(upToTime)
                .build();

        messaging.convertAndSend("/topic/room/" + payload.getRoomId(), event);

        log.debug("[WS] READ_RECEIPT roomId={} reader={}", payload.getRoomId(), readerId);
    }

    // ── Inbound: delivery status update (DM tick changes) ──
    //
    //  When user B opens a DM chat with user A, user B marks A's messages
    //  as READ via the REST API. But user A never finds out in real-time
    //  because no WebSocket event is broadcast. This endpoint solves that:
    //  user B sends a STATUS_UPDATE frame, and we broadcast a MESSAGE_STATUS
    //  event to the message sender's personal topic.

    @MessageMapping("/chat.status")
    public void handleStatusUpdate(@Payload StatusUpdatePayload payload,
                                    SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long   readerId  = resolveUserId(sessionId, headerAccessor);

        if (readerId == null || payload.getMessageId() == null || payload.getSenderId() == null) return;

        DeliveryStatusEvent event = DeliveryStatusEvent.builder()
                .messageId(payload.getMessageId())
                .roomId(payload.getRoomId())
                .status(payload.getStatus() != null ? payload.getStatus() : "READ")
                .build();

        // Send to the original sender's personal topic so their UI updates the tick
        messaging.convertAndSend("/topic/user/" + payload.getSenderId(), event);

        // Also send to the reader's personal topic so their local state stays consistent
        if (!readerId.equals(payload.getSenderId())) {
            messaging.convertAndSend("/topic/user/" + readerId, event);
        }

        log.debug("[WS] STATUS_UPDATE messageId={} sender={} status={} by reader={}",
                  payload.getMessageId(), payload.getSenderId(), payload.getStatus(), readerId);
    }

    // ── Inbound: emoji reaction ───────────────────────────

    @MessageMapping("/chat.react")
    public void handleReaction(@Payload ReactionPayload payload,
                                SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long   senderId  = resolveUserId(sessionId, headerAccessor);

        if (senderId == null) return;

        ReactionEvent event = ReactionEvent.builder()
                .senderId(senderId)
                .messageId(payload.getMessageId())
                .roomId(payload.getRoomId())
                .emoji(payload.getEmoji())
                .build();

        messaging.convertAndSend("/topic/room/" + payload.getRoomId(), event);

        log.debug("[WS] REACTION messageId={} emoji={} by userId={}",
                  payload.getMessageId(), payload.getEmoji(), senderId);
    }

    // ── Inbound: heartbeat ping ───────────────────────────

    @MessageMapping("/chat.ping")
    public void handlePing(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = resolveUserId(sessionId, headerAccessor);
        if (sessionId != null && userId != null) {
            downstream.pingPresence(sessionId);
        }
    }

    // ── Inbound: presence status update ──────────────────
    //
    //  Client fires this after a successful REST PUT /presence/status
    //  so the new status is immediately broadcast to all /topic/presence
    //  subscribers in real time — no 30-second poll needed.

    @MessageMapping("/chat.presence")
    public void handlePresenceUpdate(
            @Payload PresenceUpdatePayload payload,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        Long   userId    = resolveUserId(sessionId, headerAccessor);
        if (userId == null || payload.getStatus() == null) return;

        String status = switch (payload.getStatus().toUpperCase()) {
            case "AWAY"      -> "AWAY";
            case "DND"       -> "DND";
            case "INVISIBLE" -> "INVISIBLE";
            default          -> "ONLINE";
        };

        PresenceUpdateEvent event = PresenceUpdateEvent.builder()
                .userId(userId)
                .status(status)
                .customMessage(payload.getCustomMessage())
                .build();
        messaging.convertAndSend("/topic/presence", event);

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String token = attrs != null ? (String) attrs.get("token") : null;
        downstream.updatePresenceStatus(userId, status, token);

        log.info("[WS] PRESENCE_UPDATE userId={} status={}", userId, status);
    }

    // ── Inbound: edit message + broadcast ────────────────
    //
    //  Client sends { messageId, content, roomId?, recipientId? }.
    //  Backend persists via message-service then fans out MESSAGE_EDIT
    //  to the room topic (group) or both personal queues (DM).

    @MessageMapping("/chat.edit")
    public void handleEditMessage(
            @Payload EditPayload payload,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        Long   editorId  = resolveUserId(sessionId, headerAccessor);
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String authToken  = attrs != null ? (String) attrs.get("token") : null;

        if (editorId == null || payload.getMessageId() == null
                || payload.getContent() == null || payload.getContent().isBlank()) return;

        downstream.editMessage(payload.getMessageId(), payload.getContent(), authToken)
                .subscribe(result -> {
                    MessageEditEvent evt = MessageEditEvent.builder()
                            .editorId(editorId)
                            .messageId(payload.getMessageId())
                            .roomId(payload.getRoomId())
                            .newContent(payload.getContent())
                            .editedAt(LocalDateTime.now())
                            .build();

                    if (payload.getRoomId() != null) {
                        messaging.convertAndSend("/topic/room/" + payload.getRoomId(), evt);
                    } else {
                        messaging.convertAndSend("/topic/user/" + editorId, evt);
                        if (payload.getRecipientId() != null
                                && !payload.getRecipientId().equals(editorId)) {
                            messaging.convertAndSend("/topic/user/" + payload.getRecipientId(), evt);
                        }
                    }
                    log.debug("[WS] MESSAGE_EDIT messageId={} by userId={}",
                              payload.getMessageId(), editorId);
                });
    }

    // ── Inbound: delete message + broadcast ──────────────
    //
    //  Client sends { messageId, roomId?, recipientId?, adminDelete }.
    //  Backend deletes via message-service then fans out MESSAGE_DELETE
    //  to all participants so the bubble disappears immediately.

    @MessageMapping("/chat.delete")
    public void handleDeleteMessage(
            @Payload DeletePayload payload,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        Long   deleterId = resolveUserId(sessionId, headerAccessor);
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String authToken  = attrs != null ? (String) attrs.get("token") : null;

        if (deleterId == null || payload.getMessageId() == null) return;

        boolean isAdmin = attrs != null && "ADMIN".equalsIgnoreCase((String) attrs.get("role"));
        boolean useAdminEndpoint = Boolean.TRUE.equals(payload.getIsAdminDelete()) && isAdmin;

        downstream.deleteMessage(payload.getMessageId(), useAdminEndpoint, authToken)
                .subscribe(ignored -> {
                    MessageDeleteEvent evt = MessageDeleteEvent.builder()
                            .deleterId(deleterId)
                            .messageId(payload.getMessageId())
                            .roomId(payload.getRoomId())
                            .build();

                    if (payload.getRoomId() != null) {
                        messaging.convertAndSend("/topic/room/" + payload.getRoomId(), evt);
                    } else {
                        messaging.convertAndSend("/topic/user/" + deleterId, evt);
                        if (payload.getRecipientId() != null
                                && !payload.getRecipientId().equals(deleterId)) {
                            messaging.convertAndSend("/topic/user/" + payload.getRecipientId(), evt);
                        }
                    }
                    log.debug("[WS] MESSAGE_DELETE messageId={} by userId={} admin={}",
                              payload.getMessageId(), deleterId, useAdminEndpoint);
                });
    }

    // ── Helper: scan message for @mentions ───────────────


    private void handleMentions(String content, Long senderId, String senderName,
                                 Long roomId, Long messageId) {
        // Simple pattern: @username — notify each mentioned user
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("@(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String mentionedUsername = matcher.group(1);
            log.debug("[WS] Mention detected: @{} by userId={}", mentionedUsername, senderId);

            // In production: resolve username → userId via auth-service
            // For now broadcast to /topic/mentions/{username}
            PersonalAlertEvent alert = PersonalAlertEvent.builder()
                    .alertType("MENTION")
                    .title("You were mentioned")
                    .message(senderName + " mentioned you in a message")
                    .roomId(roomId)
                    .messageId(messageId)
                    .actorId(senderId)
                    .build();

            messaging.convertAndSend("/topic/mentions/" + mentionedUsername, alert);
        }
    }

    // ── Helper: send notifications to room members ────────

    private void notifyRoomMembers(Long roomId, Long senderId, String senderName,
                                    Long messageId, String messageContent,
                                    String authToken) {
        downstream.getRoomDetails(roomId, authToken).subscribe(roomData -> {
            if (roomData == null) {
                log.warn("[WS] Could not fetch room details for roomId={}", roomId);
                return;
            }

            // Extract members list from response
            Object membersObj = roomData.get("members");
            if (!(membersObj instanceof java.util.List<?> membersList)) {
                log.debug("[WS] No members list in room response for roomId={}", roomId);
                return;
            }

            // Send notification to each member except the sender
            for (Object member : membersList) {
                if (member instanceof java.util.Map<?, ?> memberMap) {
                    Object userIdObj = memberMap.get("userId");
                    if (userIdObj instanceof Number) {
                        Long recipientId = ((Number) userIdObj).longValue();
                        if (!recipientId.equals(senderId)) {
                            String preview = messageContent != null && messageContent.length() > 50
                                    ? messageContent.substring(0, 50) + "..."
                                    : messageContent;

                            downstream.sendNotification(
                                    recipientId,
                                    senderId,
                                    "NEW_MESSAGE",
                                    senderName + " posted in a group",
                                    preview,
                                    roomId,
                                    messageId,
                                    authToken
                            );
                        }
                    }
                }
            }
            log.debug("[WS] Sent notifications for roomId={} to {} members (excluding sender)", 
                    roomId, membersList.size() - 1);
        });
    }

    // ── Helper ────────────────────────────────────────────

    private Long extractLong(Map<?, ?> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    private Map<?, ?> extractData(Map<?, ?> response) {
        if (response == null) return null;
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return dataMap;
        }
        return response;
    }

    private Long resolveUserId(String sessionId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = sessions.getUserId(sessionId);
        if (userId != null) return userId;

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) return null;

        Object raw = attrs.get("userId");
        if (raw instanceof Number number) {
            userId = number.longValue();
        } else if (raw instanceof String text) {
            try {
                userId = Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (userId != null) {
            String email = (String) attrs.get("email");
            String username = email != null ? email.split("@")[0] : "user-" + userId;
            sessions.register(sessionId, userId, username);
        }

        return userId;
    }

    private String resolveUsername(String sessionId, SimpMessageHeaderAccessor headerAccessor, Long userId) {
        String username = sessions.getUsername(sessionId);
        if (username != null) return username;

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null || userId == null) return "user-unknown";

        String email = (String) attrs.get("email");
        return email != null ? email.split("@")[0] : "user-" + userId;
    }
}
