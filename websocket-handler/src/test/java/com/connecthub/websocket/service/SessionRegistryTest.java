package com.connecthub.websocket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_storesSessionAndUserMappings() {
        registry.register("sess-1", 1L, "alice");

        assertEquals(1L,     registry.getUserId("sess-1"));
        assertEquals("alice",registry.getUsername("sess-1"));
        assertTrue(registry.getSessionsForUser(1L).contains("sess-1"));
    }

    @Test
    void register_withNullUsername_defaultsToUserPrefix() {
        registry.register("sess-1", 2L, null);

        assertEquals("user-2", registry.getUsername("sess-1"));
    }

    @Test
    void register_multipleSessionsSameUser_allTracked() {
        registry.register("sess-a", 1L, "alice");
        registry.register("sess-b", 1L, "alice");

        Set<String> sessions = registry.getSessionsForUser(1L);
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("sess-a"));
        assertTrue(sessions.contains("sess-b"));
    }

    @Test
    void register_differentUsers_keptSeparate() {
        registry.register("sess-1", 1L, "alice");
        registry.register("sess-2", 2L, "bob");

        assertEquals(1L, registry.getUserId("sess-1"));
        assertEquals(2L, registry.getUserId("sess-2"));
    }

    // ── deregister ────────────────────────────────────────────────────────────

    @Test
    void deregister_removesSessionMappings() {
        registry.register("sess-1", 1L, "alice");
        registry.deregister("sess-1");

        assertNull(registry.getUserId("sess-1"));
        assertEquals("unknown", registry.getUsername("sess-1"));
        assertFalse(registry.getSessionsForUser(1L).contains("sess-1"));
    }

    @Test
    void deregister_lastSession_removesUserEntry() {
        registry.register("sess-1", 1L, "alice");
        registry.deregister("sess-1");

        // userToSessions entry should be gone — user is no longer online
        assertFalse(registry.isUserOnline(1L));
        assertEquals(0, registry.getTotalOnlineUsers());
    }

    @Test
    void deregister_oneOfManySessions_otherSessionsRemain() {
        registry.register("sess-a", 1L, "alice");
        registry.register("sess-b", 1L, "alice");
        registry.deregister("sess-a");

        assertTrue(registry.isUserOnline(1L));
        assertFalse(registry.getSessionsForUser(1L).contains("sess-a"));
        assertTrue(registry.getSessionsForUser(1L).contains("sess-b"));
    }

    @Test
    void deregister_unknownSession_doesNotThrow() {
        assertDoesNotThrow(() -> registry.deregister("ghost-session"));
    }

    @Test
    void deregister_sessionWithNoUserMapping_doesNotThrow() {
        // Insert a session into sessionToUser only, bypassing register,
        // to hit the "userId != null, sessions == null" branch.
        // We simulate by registering normally and checking the null-guard.
        registry.register("sess-only", 99L, "temp");
        // deregister removes sessionToUser entry; calling again should be safe
        registry.deregister("sess-only");
        assertDoesNotThrow(() -> registry.deregister("sess-only")); // userId will be null now
    }

    // ── getUserId ─────────────────────────────────────────────────────────────

    @Test
    void getUserId_unknownSession_returnsNull() {
        assertNull(registry.getUserId("not-registered"));
    }

    // ── getUsername ───────────────────────────────────────────────────────────

    @Test
    void getUsername_unknownSession_returnsUnknown() {
        assertEquals("unknown", registry.getUsername("not-registered"));
    }

    // ── getSessionsForUser ────────────────────────────────────────────────────

    @Test
    void getSessionsForUser_noSessions_returnsEmpty() {
        Set<String> sessions = registry.getSessionsForUser(999L);
        assertTrue(sessions.isEmpty());
    }

    // ── isUserOnline ──────────────────────────────────────────────────────────

    @Test
    void isUserOnline_registeredUser_returnsTrue() {
        registry.register("sess-1", 1L, "alice");
        assertTrue(registry.isUserOnline(1L));
    }

    @Test
    void isUserOnline_unregisteredUser_returnsFalse() {
        assertFalse(registry.isUserOnline(999L));
    }

    @Test
    void isUserOnline_afterDeregisterAll_returnsFalse() {
        registry.register("sess-1", 1L, "alice");
        registry.deregister("sess-1");
        assertFalse(registry.isUserOnline(1L));
    }

    // ── getTotalConnections ───────────────────────────────────────────────────

    @Test
    void getTotalConnections_reflectsSessionCount() {
        assertEquals(0, registry.getTotalConnections());

        registry.register("s1", 1L, "alice");
        assertEquals(1, registry.getTotalConnections());

        registry.register("s2", 2L, "bob");
        assertEquals(2, registry.getTotalConnections());

        registry.deregister("s1");
        assertEquals(1, registry.getTotalConnections());
    }

    // ── getTotalOnlineUsers ───────────────────────────────────────────────────

    @Test
    void getTotalOnlineUsers_deduplicatesMultiDeviceUsers() {
        registry.register("sess-a", 1L, "alice");
        registry.register("sess-b", 1L, "alice"); // same user, 2nd device
        registry.register("sess-c", 2L, "bob");

        assertEquals(2, registry.getTotalOnlineUsers()); // alice + bob, not 3
    }

    // ── getAllOnlineUserIds ────────────────────────────────────────────────────

    @Test
    void getAllOnlineUserIds_returnsAllDistinctUserIds() {
        registry.register("s1", 10L, "user10");
        registry.register("s2", 20L, "user20");
        registry.register("s3", 10L, "user10"); // duplicate device

        List<Long> ids = registry.getAllOnlineUserIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains(10L));
        assertTrue(ids.contains(20L));
    }

    @Test
    void getAllOnlineUserIds_empty_returnsEmptyList() {
        assertTrue(registry.getAllOnlineUserIds().isEmpty());
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsCorrectMapKeys() {
        registry.register("s1", 1L, "alice");
        registry.register("s2", 1L, "alice");
        registry.register("s3", 2L, "bob");

        Map<String, Object> stats = registry.getStats();

        assertEquals(3,  stats.get("totalConnections")); // 3 sessions
        assertEquals(2,  stats.get("totalOnlineUsers")); // 2 unique users
        assertTrue(stats.get("onlineUserIds") instanceof List);

        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) stats.get("onlineUserIds");
        assertEquals(2, ids.size());
    }

    @Test
    void getStats_empty_allZero() {
        Map<String, Object> stats = registry.getStats();
        assertEquals(0, stats.get("totalConnections"));
        assertEquals(0, stats.get("totalOnlineUsers"));
    }
}
