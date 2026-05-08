package com.connecthub.presence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * UserPresence is stored as a Redis Hash.
 *
 * Key pattern:  presence:<presenceId>
 * Index on:     userId   — find presence by userId
 *               status   — find all users with ONLINE status
 *               sessionId — find presence by WebSocket session
 *
 * TTL: 120 seconds — every WebSocket heartbeat resets this.
 * If TTL expires, Redis evicts the record automatically,
 * acting as a safety net on top of the scheduled stale cleanup.
 */
@RedisHash(value = "presence", timeToLive = 120)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPresence implements Serializable {

    @Id
    private String presenceId;   // sessionId used as the Redis key

    @Indexed
    private Long userId;

    @Indexed
    private String status;       // ONLINE | AWAY | DND | INVISIBLE

    private String customMessage;

    @Indexed
    private String deviceType;   // WEB | MOBILE | DESKTOP

    private String ipAddress;

    private LocalDateTime connectedAt;

    private LocalDateTime lastPingAt;

    @Indexed
    private String sessionId;    // WebSocket sessionId

    @TimeToLive
    private Long ttl = 120L;     // seconds — reset on every ping

    public enum UserStatus {
        ONLINE, AWAY, DND, INVISIBLE
    }
}
