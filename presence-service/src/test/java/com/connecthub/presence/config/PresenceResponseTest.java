package com.connecthub.presence.config;

import com.connecthub.presence.dto.response.ApiResponse;
import com.connecthub.presence.dto.response.PresenceResponse;
import com.connecthub.presence.entity.UserPresence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the static factory methods of {@link PresenceResponse} and
 * {@link ApiResponse} — these are in dto/** but it's worth verifying
 * the isOnline logic which is business-relevant.
 *
 * (JaCoCo excludes dto/** per pom.xml, but these tests guard correctness
 * and do no harm to coverage metrics.)
 */
class PresenceResponseTest {

    private UserPresence buildPresence(Long userId, String sessionId, String status) {
        return UserPresence.builder()
                .userId(userId)
                .status(status)
                .sessionId(sessionId)
                .deviceType("WEB")
                .customMessage("msg")
                .connectedAt(LocalDateTime.now())
                .lastPingAt(LocalDateTime.now())
                .build();
    }

    @Test
    void from_onlineStatus_isOnlineTrue() {
        PresenceResponse r = PresenceResponse.from(buildPresence(1L, "s", "ONLINE"));
        assertTrue(r.getIsOnline());
        assertEquals("ONLINE", r.getStatus());
        assertEquals(1L,       r.getUserId());
        assertEquals("s",      r.getSessionId());
        assertEquals("WEB",    r.getDeviceType());
        assertEquals("msg",    r.getCustomMessage());
        assertNotNull(r.getConnectedAt());
        assertNotNull(r.getLastPingAt());
    }

    @Test
    void from_awayStatus_isOnlineTrue() {
        PresenceResponse r = PresenceResponse.from(buildPresence(2L, "s2", "AWAY"));
        assertTrue(r.getIsOnline());
    }

    @Test
    void from_dndStatus_isOnlineFalse() {
        PresenceResponse r = PresenceResponse.from(buildPresence(3L, "s3", "DND"));
        assertFalse(r.getIsOnline());
    }

    @Test
    void from_invisibleStatus_isOnlineFalse() {
        PresenceResponse r = PresenceResponse.from(buildPresence(4L, "s4", "INVISIBLE"));
        assertFalse(r.getIsOnline());
    }

    @Test
    void offline_returnsCorrectStub() {
        PresenceResponse r = PresenceResponse.offline(42L);
        assertEquals(42L,        r.getUserId());
        assertEquals("INVISIBLE",r.getStatus());
        assertFalse(r.getIsOnline());
        assertNull(r.getSessionId());
    }

    // ── ApiResponse ──────────────────────────────────────────────────────────

    @Test
    void apiResponse_successWithMessageAndData() {
        ApiResponse<String> r = ApiResponse.success("hello", "data");
        assertTrue(r.isSuccess());
        assertEquals("hello", r.getMessage());
        assertEquals("data",  r.getData());
        assertNotNull(r.getTimestamp());
    }

    @Test
    void apiResponse_successDataOnly() {
        ApiResponse<Integer> r = ApiResponse.success(99);
        assertTrue(r.isSuccess());
        assertEquals("Success", r.getMessage());
        assertEquals(99,        r.getData());
    }

    @Test
    void apiResponse_error() {
        ApiResponse<Void> r = ApiResponse.error("Something went wrong");
        assertFalse(r.isSuccess());
        assertEquals("Something went wrong", r.getMessage());
        assertNull(r.getData());
    }
}
