package com.connecthub.presence.service;

import com.connecthub.presence.dto.request.*;
import com.connecthub.presence.dto.response.PresenceResponse;

import java.util.List;

public interface PresenceService {

    // ── Lifecycle ─────────────────────────────────────────
    PresenceResponse setOnline(SetOnlineRequest request);

    void setOffline(Long userId, String sessionId);

    void setOfflineBySessionId(String sessionId);

    // ── Status ────────────────────────────────────────────
    PresenceResponse updateStatus(Long userId, UpdateStatusRequest request);

    // ── Read ──────────────────────────────────────────────
    PresenceResponse getPresence(Long userId);

    List<PresenceResponse> getBulkPresence(List<Long> userIds);

    List<PresenceResponse> getOnlineUsers();

    int getOnlineCount();

    boolean isOnline(Long userId);

    // ── Heartbeat ─────────────────────────────────────────
    void ping(String sessionId);

    // ── Maintenance ───────────────────────────────────────
    void cleanStaleSessions();
}
