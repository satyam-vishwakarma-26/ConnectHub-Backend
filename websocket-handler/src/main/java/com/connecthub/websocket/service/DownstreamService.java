package com.connecthub.websocket.service;

import com.connecthub.websocket.payload.StompPayloads.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DownstreamService — all fire-and-forget calls to other microservices.
 *
 * Every method is non-blocking (reactive). Failures are logged but
 * NEVER propagate back to the WebSocket session — the real-time path
 * must never stall because a downstream service is slow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownstreamService {

    private final WebClient messageClient;
    private final WebClient presenceClient;
    private final WebClient notificationClient;
    private final WebClient roomClient;
    private final WebClient authClient;

    // ── Persist a new message ─────────────────────────────

        public Mono<Map> persistMessage(Long senderId, Long roomId, String content,
                                                                         String type, String mediaUrl, Long replyToId,
                                                                         String authToken) {
                Map<String, Object> body = new HashMap<>();
                body.put("roomId", roomId);
                body.put("content", content != null ? content : "");
                body.put("type", type != null ? type : "TEXT");
                body.put("mediaUrl", mediaUrl != null ? mediaUrl : "");
                if (replyToId != null && replyToId > 0) {
                        body.put("replyToMessageId", replyToId);
                }

        return messageClient.post()
                .uri("/messages")
                                .header("Authorization", "Bearer " + authToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnSuccess(r -> log.debug("[DS] Message persisted roomId={} sender={}", roomId, senderId))
                .doOnError(e  -> log.warn("[DS] persistMessage failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

        // ── Persist a direct (1:1) message ───────────────────

        public Mono<Map> persistDirectMessage(Long senderId, Long recipientId, String content,
                                                                                  String type, String mediaUrl, Long replyToId,
                                                                                  String authToken) {
                Map<String, Object> body = new HashMap<>();
                body.put("recipientId", recipientId);
                body.put("content", content != null ? content : "");
                body.put("type", type != null ? type : "TEXT");
                body.put("mediaUrl", mediaUrl != null ? mediaUrl : "");
                if (replyToId != null && replyToId > 0) {
                        body.put("replyToMessageId", replyToId);
                }

                return messageClient.post()
                                .uri("/messages/direct")
                                .header("Authorization", "Bearer " + authToken)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .doOnSuccess(r -> log.debug("[DS] Direct message persisted sender={} recipient={}", senderId, recipientId))
                                .doOnError(e  -> log.warn("[DS] persistDirectMessage failed: {}", e.getMessage()))
                                .onErrorResume(e -> Mono.empty());
        }

    // ── Update delivery status ────────────────────────────

    public void updateDeliveryStatus(Long messageId, Long readerId, String status) {
        messageClient.put()
                .uri("/messages/{id}/status", messageId)
                .header("X-Auth-User-Id", String.valueOf(readerId))
                .bodyValue(Map.of("status", status))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] updateDeliveryStatus failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Edit an existing message (used by /app/chat.edit) ─

    public Mono<Map> editMessage(Long messageId, String content, String authToken) {
        return messageClient.put()
                .uri("/messages/{id}", messageId)
                .header("Authorization", "Bearer " + authToken)
                .bodyValue(Map.of("content", content))
                .retrieve()
                .bodyToMono(Map.class)
                .doOnSuccess(r -> log.debug("[DS] Message edited messageId={}", messageId))
                .doOnError(e  -> log.warn("[DS] editMessage failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    // ── Delete a message (used by /app/chat.delete) ───────
    // isAdminDelete=true  → DELETE /messages/admin/{id}  (any message)
    // isAdminDelete=false → DELETE /messages/{id}        (own message only)

    public Mono<Boolean> deleteMessage(Long messageId, boolean isAdminDelete, String authToken) {
        String uri = isAdminDelete
                ? "/messages/admin/" + messageId
                : "/messages/" + messageId;
        return messageClient.delete()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(v -> log.debug("[DS] Message deleted messageId={} admin={}", messageId, isAdminDelete))
                .onErrorResume(e -> {
                    log.warn("[DS] deleteMessage failed (might be already deleted): {}", e.getMessage());
                    return Mono.just(true);
                })
                .defaultIfEmpty(true);
    }

    // ── Mark room messages as READ ────────────────────────

    public void markRoomRead(Long roomId, Long readerId, String upToTime) {
        messageClient.put()
                .uri("/messages/room/{roomId}/read?upToTime={t}", roomId, upToTime)
                .header("X-Auth-User-Id", String.valueOf(readerId))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] markRoomRead failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        // Also update lastReadAt in room-service
        roomClient.put()
                .uri("/rooms/{roomId}/read", roomId)
                .header("X-Auth-User-Id", String.valueOf(readerId))
                .bodyValue(Map.of("readAt", upToTime))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] updateLastRead room-service failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Presence: set online ─────────────────────────────

    public void setUserOnline(Long userId, String sessionId, String deviceType, String ipAddress) {
        Map<String, Object> body = Map.of(
                "userId",     userId,
                "sessionId",  sessionId,
                "deviceType", deviceType != null ? deviceType : "WEB",
                "ipAddress",  ipAddress  != null ? ipAddress  : "",
                "status",     "ONLINE"
        );

        presenceClient.post()
                .uri("/presence/online")
                .header("X-Auth-User-Id", String.valueOf(userId))   // auth for presence-service
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] setUserOnline failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Presence: set offline ────────────────────────────

    public void setUserOffline(Long userId, String sessionId, String authToken) {
        presenceClient.post()
                .uri("/presence/offline/session/{sessionId}", sessionId)
                .header("X-Auth-User-Id", String.valueOf(userId))   // auth for presence-service
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] setUserOffline failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        // Record lastSeenAt in auth-service (best effort)
        if (authToken != null && !authToken.isBlank()) {
            recordLastSeen(authToken);
        }
        log.debug("[DS] User offline: userId={} session={}", userId, sessionId);
    }

    public void recordLastSeen(String authToken) {
        if (authToken == null || authToken.isBlank()) return;

        authClient.post()
                .uri("/auth/last-seen")
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(v -> log.debug("[DS] Recorded last seen in auth-service"))
                .doOnError(e -> log.warn("[DS] recordLastSeen failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Presence: heartbeat ping ────────────────────────────

    public void pingPresence(String sessionId) {
        presenceClient.post()
                .uri("/presence/ping/{sessionId}", sessionId)
                .header("X-Auth-User-Id", "0")   // presence-service only checks existence
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.debug("[DS] ping failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Presence: update status (ONLINE/AWAY/DND/INVISIBLE) ──

    public void updatePresenceStatus(Long userId, String status, String authToken) {
        var request = presenceClient.put()
                .uri("/presence/status");
        if (authToken != null && !authToken.isBlank()) {
            request = request.header("Authorization", "Bearer " + authToken);
        }
        request
                .header("X-Auth-User-Id", String.valueOf(userId))
                .bodyValue(Map.of("status", status))
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] updatePresenceStatus failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    // ── Room: get room details with members ────────────────

    public Mono<Map> getRoomDetails(Long roomId) {
                return getRoomDetails(roomId, null);
        }

        public Mono<Map> getRoomDetails(Long roomId, String authToken) {
                var request = roomClient.get()
                .uri("/rooms/{roomId}", roomId)
                                ;

                if (authToken != null && !authToken.isBlank()) {
                        request = request.header("Authorization", "Bearer " + authToken);
                }

                return request
                .retrieve()
                .bodyToMono(Map.class)
                .doOnError(e -> log.warn("[DS] getRoomDetails failed for roomId={}: {}", roomId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    // ── Notification: send in-app ─────────────────────────

    public void sendNotification(Long recipientId, Long actorId, String type,
                                  String title, String message, Long roomId, Long messageId) {
                sendNotification(recipientId, actorId, type, title, message, roomId, messageId, null);
        }

        public void sendNotification(Long recipientId, Long actorId, String type,
                                                                 String title, String message, Long roomId, Long messageId,
                                                                 String authToken) {
                Map<String, Object> body = new HashMap<>();
                body.put("recipientId", recipientId);
                body.put("type", type != null && !type.isBlank() ? type : "SYSTEM");
                body.put("title", title != null && !title.isBlank() ? title : "ConnectHub");
                body.put("message", message != null ? message : "You have a new notification.");

                if (actorId != null) {
                        body.put("actorId", actorId);
                }
                if (roomId != null) {
                        body.put("roomId", roomId);
                }
                if (messageId != null) {
                        body.put("messageId", messageId);
                }

                var request = notificationClient.post()
                                .uri("/notifications/send");

                if (authToken != null && !authToken.isBlank()) {
                        request = request.header("Authorization", "Bearer " + authToken);
                }

                request
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity()
                        .doOnError(e -> log.warn("[DS] sendNotification failed: {}", e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                        .subscribe();
    }

    // ── Notification: FCM push ────────────────────────────

    public void sendPush(Long userId, String title, String body) {
        notificationClient.post()
                .uri("/notifications/push?userId={uid}&title={t}&body={b}", userId, title, body)
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("[DS] sendPush failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
