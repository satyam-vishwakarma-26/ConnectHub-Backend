package com.connecthub.presence.service.impl;

import com.connecthub.presence.dto.request.*;
import com.connecthub.presence.dto.response.PresenceResponse;
import com.connecthub.presence.entity.UserPresence;
import com.connecthub.presence.repository.PresenceRepository;
import com.connecthub.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceServiceImpl implements PresenceService {

    private final PresenceRepository presenceRepository;

    @Value("${app.presence.stale-threshold-seconds:60}")
    private long staleThresholdSeconds;

    private volatile boolean redisUnavailableLogged = false;

    // ── Set Online ────────────────────────────────────────

    @Override
    public PresenceResponse setOnline(SetOnlineRequest request) {
        // One record per sessionId — supports multi-device
        UserPresence presence = UserPresence.builder()
                .presenceId(request.getSessionId())
                .userId(request.getUserId())
                .status(parseStatus(request.getStatus()))
                .deviceType(request.getDeviceType() != null ? request.getDeviceType() : "WEB")
                .ipAddress(request.getIpAddress())
                .sessionId(request.getSessionId())
                .connectedAt(LocalDateTime.now())
                .lastPingAt(LocalDateTime.now())
                .ttl(120L)
                .build();

        presenceRepository.save(presence);
        log.info("[Presence] ONLINE  userId={} session={} device={}",
                  request.getUserId(), request.getSessionId(), presence.getDeviceType());
        return PresenceResponse.from(presence);
    }

    // ── Set Offline ───────────────────────────────────────

    @Override
    public void setOffline(Long userId, String sessionId) {
        if (sessionId != null) {
            presenceRepository.deleteBySessionId(sessionId);
            log.info("[Presence] OFFLINE userId={} session={}", userId, sessionId);
        } else {
            // Full logout — remove all sessions
            presenceRepository.deleteByUserId(userId);
            log.info("[Presence] OFFLINE ALL userId={}", userId);
        }
    }

    @Override
    public void setOfflineBySessionId(String sessionId) {
        presenceRepository.findBySessionId(sessionId).ifPresent(p -> {
            presenceRepository.deleteBySessionId(sessionId);
            log.info("[Presence] OFFLINE sessionId={} userId={}", sessionId, p.getUserId());
        });
    }

    // ── Update Status ─────────────────────────────────────

    @Override
    public PresenceResponse updateStatus(Long userId, UpdateStatusRequest request) {
        List<UserPresence> sessions = presenceRepository.findByUserId(userId);

        if (sessions.isEmpty()) {
            // User is not connected — return offline stub
            return PresenceResponse.offline(userId);
        }

        String newStatus = parseStatus(request.getStatus());

        sessions.forEach(p -> {
            p.setStatus(newStatus);
            if (request.getCustomMessage() != null) {
                p.setCustomMessage(request.getCustomMessage());
            }
            p.setLastPingAt(LocalDateTime.now());
            presenceRepository.save(p);
        });

        log.info("[Presence] STATUS userId={} → {}", userId, newStatus);
        return PresenceResponse.from(sessions.get(0));
    }

    // ── Get Presence ──────────────────────────────────────

    @Override
    public PresenceResponse getPresence(Long userId) {
        List<UserPresence> sessions = presenceRepository.findByUserId(userId);
        if (sessions.isEmpty()) return PresenceResponse.offline(userId);

        // If the user has multiple sessions, prefer the most recently active one
        return sessions.stream()
                .max(Comparator.comparing(p ->
                        p.getLastPingAt() != null ? p.getLastPingAt() : LocalDateTime.MIN))
                .map(PresenceResponse::from)
                .orElse(PresenceResponse.offline(userId));
    }

    // ── Bulk Presence ─────────────────────────────────────

    @Override
    public List<PresenceResponse> getBulkPresence(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();

        // Spring Data Redis does not support 'In' query derivation,
        // so we fetch each user individually and merge.
        Map<Long, UserPresence> latestByUser = new HashMap<>();
        for (Long uid : userIds) {
            List<UserPresence> sessions = presenceRepository.findByUserId(uid);
            for (UserPresence p : sessions) {
                latestByUser.merge(p.getUserId(), p, (existing, incoming) -> {
                    LocalDateTime et = existing.getLastPingAt();
                    LocalDateTime it = incoming.getLastPingAt();
                    if (et == null) return incoming;
                    if (it == null) return existing;
                    return it.isAfter(et) ? incoming : existing;
                });
            }
        }

        // Return a response for every requested userId — offline stub if not found
        return userIds.stream()
                .map(id -> latestByUser.containsKey(id)
                        ? PresenceResponse.from(latestByUser.get(id))
                        : PresenceResponse.offline(id))
                .collect(Collectors.toList());
    }

    // ── Online Users ──────────────────────────────────────

    @Override
    public List<PresenceResponse> getOnlineUsers() {
        List<UserPresence> online = presenceRepository.findByStatus("ONLINE");
        // Deduplicate by userId
        return online.stream()
                .collect(Collectors.toMap(
                        UserPresence::getUserId,
                        p -> p,
                        (a, b) -> a.getLastPingAt() != null &&
                                  b.getLastPingAt() != null &&
                                  a.getLastPingAt().isAfter(b.getLastPingAt()) ? a : b))
                .values().stream()
                .map(PresenceResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public int getOnlineCount() {
        return presenceRepository.findByStatus("ONLINE").size();
    }

    @Override
    public boolean isOnline(Long userId) {
        return presenceRepository.findByUserId(userId).stream()
                .anyMatch(p -> "ONLINE".equals(p.getStatus()) || "AWAY".equals(p.getStatus()));
    }

    // ── Heartbeat Ping ────────────────────────────────────

    @Override
    public void ping(String sessionId) {
        presenceRepository.findBySessionId(sessionId).ifPresent(p -> {
            p.setLastPingAt(LocalDateTime.now());
            p.setTtl(120L);  // Reset Redis TTL
            presenceRepository.save(p);
            log.debug("[Presence] PING sessionId={} userId={}", sessionId, p.getUserId());
        });
    }

    // ── Stale Session Cleanup (scheduled) ────────────────

    @Override
    @Scheduled(fixedDelayString = "${app.presence.cleanup-interval-ms:60000}")
    public void cleanStaleSessions() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleThresholdSeconds);

            Iterable<UserPresence> all = presenceRepository.findAll();
            List<UserPresence> stale = StreamSupport
                    .stream(all.spliterator(), false)
                    .filter(p -> p.getLastPingAt() != null && p.getLastPingAt().isBefore(cutoff))
                    .collect(Collectors.toList());

            if (!stale.isEmpty()) {
                stale.forEach(p -> presenceRepository.deleteBySessionId(p.getSessionId()));
                log.info("[Presence] Cleaned {} stale sessions (cutoff={})", stale.size(), cutoff);
            }

            if (redisUnavailableLogged) {
                log.info("[Presence] Redis connection restored");
                redisUnavailableLogged = false;
            }
        } catch (RedisConnectionFailureException ex) {
            if (!redisUnavailableLogged) {
                log.warn("[Presence] Redis unavailable at {}:{}; stale cleanup paused until Redis is reachable",
                        "localhost", 6379);
                redisUnavailableLogged = true;
            }
        }
    }

    // ── Helper ────────────────────────────────────────────

    private String parseStatus(String status) {
        if (status == null) return "ONLINE";
        return switch (status.toUpperCase()) {
            case "AWAY"      -> "AWAY";
            case "DND"       -> "DND";
            case "INVISIBLE" -> "INVISIBLE";
            default          -> "ONLINE";
        };
    }
}
