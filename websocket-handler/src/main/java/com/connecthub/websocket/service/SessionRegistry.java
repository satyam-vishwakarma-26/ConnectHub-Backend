package com.connecthub.websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SessionRegistry — thread-safe in-memory store of active WebSocket sessions.
 *
 * Maps:
 *   sessionId  → userId      (every connected session)
 *   userId     → Set<sessionId>  (user may have multiple devices)
 *
 * Used by ChatController to:
 *   - Look up userId from a Spring WebSocket sessionId
 *   - Check if a userId has any active sessions (for DELIVERED status)
 *   - Get the total count of active connections (admin dashboard)
 */
@Component
@Slf4j
public class SessionRegistry {

    // sessionId → userId
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    // userId → set of sessionIds (multi-device support)
    private final ConcurrentHashMap<Long, Set<String>> userToSessions = new ConcurrentHashMap<>();

    // sessionId → username (for display in events)
    private final ConcurrentHashMap<String, String> sessionToUsername = new ConcurrentHashMap<>();

    // ── Register ──────────────────────────────────────────

    public void register(String sessionId, Long userId, String username) {
        sessionToUser.put(sessionId, userId);
        sessionToUsername.put(sessionId, username != null ? username : "user-" + userId);
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                      .add(sessionId);
        log.debug("[Sessions] + sessionId={} userId={}", sessionId, userId);
    }

    // ── Deregister ────────────────────────────────────────

    public void deregister(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        sessionToUsername.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userToSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userToSessions.remove(userId);
                }
            }
        }
        log.debug("[Sessions] - sessionId={} userId={}", sessionId, userId);
    }

    // ── Lookups ───────────────────────────────────────────

    public Long getUserId(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    public String getUsername(String sessionId) {
        return sessionToUsername.getOrDefault(sessionId, "unknown");
    }

    public Set<String> getSessionsForUser(Long userId) {
        return userToSessions.getOrDefault(userId, Collections.emptySet());
    }

    public boolean isUserOnline(Long userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int getTotalConnections() {
        return sessionToUser.size();
    }

    public int getTotalOnlineUsers() {
        return userToSessions.size();
    }

    public List<Long> getAllOnlineUserIds() {
        return new ArrayList<>(userToSessions.keySet());
    }

    public Map<String, Object> getStats() {
        return Map.of(
                "totalConnections", getTotalConnections(),
                "totalOnlineUsers", getTotalOnlineUsers(),
                "onlineUserIds",    getAllOnlineUserIds()
        );
    }
}
