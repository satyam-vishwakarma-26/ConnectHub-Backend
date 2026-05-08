package com.connecthub.presence.service.impl;

import com.connecthub.presence.dto.request.SetOnlineRequest;
import com.connecthub.presence.dto.request.UpdateStatusRequest;
import com.connecthub.presence.dto.response.PresenceResponse;
import com.connecthub.presence.entity.UserPresence;
import com.connecthub.presence.repository.PresenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    @Mock
    private PresenceRepository presenceRepository;

    @InjectMocks
    private PresenceServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "staleThresholdSeconds", 60L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private UserPresence presence(Long userId, String sessionId, String status) {
        return UserPresence.builder()
                .presenceId(sessionId)
                .userId(userId)
                .status(status)
                .deviceType("WEB")
                .sessionId(sessionId)
                .connectedAt(LocalDateTime.now())
                .lastPingAt(LocalDateTime.now())
                .ttl(120L)
                .build();
    }

    private SetOnlineRequest onlineRequest(Long userId, String sessionId, String status, String device) {
        SetOnlineRequest r = new SetOnlineRequest();
        r.setUserId(userId);
        r.setSessionId(sessionId);
        r.setStatus(status);
        r.setDeviceType(device);
        r.setIpAddress("127.0.0.1");
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setOnline
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOnline_withExplicitStatus_savesAndReturnsResponse() {
        SetOnlineRequest req = onlineRequest(1L, "sess-1", "AWAY", "MOBILE");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertNotNull(resp);
        assertEquals(1L,      resp.getUserId());
        assertEquals("AWAY",  resp.getStatus());
        assertEquals("MOBILE",resp.getDeviceType());
        verify(presenceRepository).save(any(UserPresence.class));
    }

    @Test
    void setOnline_withNullStatus_defaultsToOnline() {
        SetOnlineRequest req = onlineRequest(2L, "sess-2", null, "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("ONLINE", resp.getStatus());
    }

    @Test
    void setOnline_withNullDeviceType_defaultsToWeb() {
        SetOnlineRequest req = onlineRequest(3L, "sess-3", "ONLINE", null);
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("WEB", resp.getDeviceType());
    }

    @Test
    void setOnline_withDndStatus_setsDnd() {
        SetOnlineRequest req = onlineRequest(4L, "sess-4", "DND", "DESKTOP");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("DND", resp.getStatus());
    }

    @Test
    void setOnline_withInvisibleStatus_setsInvisible() {
        SetOnlineRequest req = onlineRequest(5L, "sess-5", "INVISIBLE", "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("INVISIBLE", resp.getStatus());
    }

    @Test
    void setOnline_withUnknownStatus_defaultsToOnline() {
        SetOnlineRequest req = onlineRequest(6L, "sess-6", "BANANA", "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("ONLINE", resp.getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setOffline
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOffline_withSessionId_deletesSession() {
        service.setOffline(1L, "sess-1");

        verify(presenceRepository).deleteBySessionId("sess-1");
        verify(presenceRepository, never()).deleteByUserId(any());
    }

    @Test
    void setOffline_withNullSessionId_deletesAllUserSessions() {
        service.setOffline(1L, null);

        verify(presenceRepository).deleteByUserId(1L);
        verify(presenceRepository, never()).deleteBySessionId(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setOfflineBySessionId
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setOfflineBySessionId_whenSessionExists_deletesIt() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        when(presenceRepository.findBySessionId("sess-1")).thenReturn(Optional.of(p));

        service.setOfflineBySessionId("sess-1");

        verify(presenceRepository).deleteBySessionId("sess-1");
    }

    @Test
    void setOfflineBySessionId_whenSessionNotFound_doesNothing() {
        when(presenceRepository.findBySessionId("unknown")).thenReturn(Optional.empty());

        service.setOfflineBySessionId("unknown");

        verify(presenceRepository, never()).deleteBySessionId(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateStatus_whenUserHasSessions_updatesAllAndReturnsFirst() {
        UserPresence p1 = presence(1L, "sess-a", "ONLINE");
        UserPresence p2 = presence(1L, "sess-b", "ONLINE");
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p1, p2));
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateStatusRequest req = new UpdateStatusRequest();
        req.setStatus("AWAY");
        req.setCustomMessage("Be right back");

        PresenceResponse resp = service.updateStatus(1L, req);

        assertEquals("AWAY", resp.getStatus());
        assertEquals("AWAY", p1.getStatus());
        assertEquals("AWAY", p2.getStatus());
        assertEquals("Be right back", p1.getCustomMessage());
        verify(presenceRepository, times(2)).save(any(UserPresence.class));
    }

    @Test
    void updateStatus_withNullCustomMessage_doesNotOverwriteExisting() {
        UserPresence p = presence(1L, "sess-a", "ONLINE");
        p.setCustomMessage("existing message");
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p));
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateStatusRequest req = new UpdateStatusRequest();
        req.setStatus("DND");
        req.setCustomMessage(null);

        service.updateStatus(1L, req);

        assertEquals("existing message", p.getCustomMessage());
    }

    @Test
    void updateStatus_whenNoSessions_returnsOfflineStub() {
        when(presenceRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

        UpdateStatusRequest req = new UpdateStatusRequest();
        req.setStatus("ONLINE");

        PresenceResponse resp = service.updateStatus(99L, req);

        assertEquals(99L,        resp.getUserId());
        assertEquals("INVISIBLE",resp.getStatus());
        assertFalse(resp.getIsOnline());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPresence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPresence_whenNoSessions_returnsOffline() {
        when(presenceRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        PresenceResponse resp = service.getPresence(1L);

        assertEquals("INVISIBLE", resp.getStatus());
        assertFalse(resp.getIsOnline());
    }

    @Test
    void getPresence_withSingleSession_returnsIt() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p));

        PresenceResponse resp = service.getPresence(1L);

        assertEquals("ONLINE", resp.getStatus());
        assertTrue(resp.getIsOnline());
    }

    @Test
    void getPresence_withMultipleSessions_returnsLatestPing() {
        LocalDateTime earlier = LocalDateTime.now().minusMinutes(5);
        LocalDateTime later   = LocalDateTime.now();

        UserPresence old  = presence(1L, "sess-old",  "AWAY");
        old.setLastPingAt(earlier);

        UserPresence fresh = presence(1L, "sess-new", "ONLINE");
        fresh.setLastPingAt(later);

        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(old, fresh));

        PresenceResponse resp = service.getPresence(1L);

        assertEquals("sess-new", resp.getSessionId());
        assertEquals("ONLINE",   resp.getStatus());
    }

    @Test
    void getPresence_withNullLastPingAt_stillReturnsWithoutNPE() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        p.setLastPingAt(null);
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p));

        assertDoesNotThrow(() -> service.getPresence(1L));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getBulkPresence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getBulkPresence_nullInput_returnsEmpty() {
        List<PresenceResponse> result = service.getBulkPresence(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBulkPresence_emptyInput_returnsEmpty() {
        List<PresenceResponse> result = service.getBulkPresence(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void getBulkPresence_userFound_returnsPresence() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p));

        List<PresenceResponse> result = service.getBulkPresence(List.of(1L));

        assertEquals(1, result.size());
        assertEquals("ONLINE", result.get(0).getStatus());
    }

    @Test
    void getBulkPresence_userNotFound_returnsOfflineStub() {
        when(presenceRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

        List<PresenceResponse> result = service.getBulkPresence(List.of(99L));

        assertEquals(1, result.size());
        assertEquals("INVISIBLE", result.get(0).getStatus());
        assertFalse(result.get(0).getIsOnline());
    }

    @Test
    void getBulkPresence_multipleSessionsForUser_picksLatestPing() {
        LocalDateTime older  = LocalDateTime.now().minusMinutes(10);
        LocalDateTime newer  = LocalDateTime.now();

        UserPresence p1 = presence(1L, "sess-old", "AWAY");
        p1.setLastPingAt(older);

        UserPresence p2 = presence(1L, "sess-new", "ONLINE");
        p2.setLastPingAt(newer);

        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p1, p2));

        List<PresenceResponse> result = service.getBulkPresence(List.of(1L));

        assertEquals("sess-new", result.get(0).getSessionId());
    }

    @Test
    void getBulkPresence_existingSessionHasNullPingAt_incomingWins() {
        UserPresence existing = presence(1L, "sess-old", "ONLINE");
        existing.setLastPingAt(null);

        UserPresence incoming = presence(1L, "sess-new", "AWAY");
        incoming.setLastPingAt(LocalDateTime.now());

        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(existing, incoming));

        List<PresenceResponse> result = service.getBulkPresence(List.of(1L));

        // merge fn: et==null → return incoming
        assertEquals("sess-new", result.get(0).getSessionId());
    }

    @Test
    void getBulkPresence_incomingSessionHasNullPingAt_existingWins() {
        UserPresence existing = presence(1L, "sess-old", "ONLINE");
        existing.setLastPingAt(LocalDateTime.now());

        UserPresence incoming = presence(1L, "sess-new", "AWAY");
        incoming.setLastPingAt(null);

        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(existing, incoming));

        List<PresenceResponse> result = service.getBulkPresence(List.of(1L));

        // merge fn: it==null → return existing
        assertEquals("sess-old", result.get(0).getSessionId());
    }

    @Test
    void getBulkPresence_mixedFoundAndNotFound() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(p));
        when(presenceRepository.findByUserId(2L)).thenReturn(Collections.emptyList());

        List<PresenceResponse> result = service.getBulkPresence(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("ONLINE",    result.get(0).getStatus());
        assertEquals("INVISIBLE", result.get(1).getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOnlineUsers
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOnlineUsers_noUsers_returnsEmpty() {
        when(presenceRepository.findByStatus("ONLINE")).thenReturn(Collections.emptyList());

        assertTrue(service.getOnlineUsers().isEmpty());
    }

    @Test
    void getOnlineUsers_uniqueUsers_returnsAll() {
        UserPresence p1 = presence(1L, "sess-1", "ONLINE");
        UserPresence p2 = presence(2L, "sess-2", "ONLINE");
        when(presenceRepository.findByStatus("ONLINE")).thenReturn(List.of(p1, p2));

        List<PresenceResponse> result = service.getOnlineUsers();

        assertEquals(2, result.size());
    }

    @Test
    void getOnlineUsers_deduplicatesByUserId_keepsLatestPing() {
        LocalDateTime earlier = LocalDateTime.now().minusMinutes(3);
        LocalDateTime later   = LocalDateTime.now();

        UserPresence old  = presence(1L, "sess-old",  "ONLINE");
        old.setLastPingAt(earlier);

        UserPresence fresh = presence(1L, "sess-new", "ONLINE");
        fresh.setLastPingAt(later);

        when(presenceRepository.findByStatus("ONLINE")).thenReturn(List.of(old, fresh));

        List<PresenceResponse> result = service.getOnlineUsers();

        assertEquals(1, result.size());
        assertEquals("sess-new", result.get(0).getSessionId());
    }

    @Test
    void getOnlineUsers_deduplication_bothNullPingAt_keepsDeterministicResult() {
        UserPresence p1 = presence(1L, "sess-a", "ONLINE");
        p1.setLastPingAt(null);
        UserPresence p2 = presence(1L, "sess-b", "ONLINE");
        p2.setLastPingAt(null);

        when(presenceRepository.findByStatus("ONLINE")).thenReturn(List.of(p1, p2));

        // Should not throw; one is returned
        List<PresenceResponse> result = service.getOnlineUsers();
        assertEquals(1, result.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOnlineCount
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOnlineCount_returnsCorrectCount() {
        when(presenceRepository.findByStatus("ONLINE"))
                .thenReturn(List.of(presence(1L, "s1", "ONLINE"), presence(2L, "s2", "ONLINE")));

        assertEquals(2, service.getOnlineCount());
    }

    @Test
    void getOnlineCount_noOnlineUsers_returnsZero() {
        when(presenceRepository.findByStatus("ONLINE")).thenReturn(Collections.emptyList());

        assertEquals(0, service.getOnlineCount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isOnline
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isOnline_userIsOnline_returnsTrue() {
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(presence(1L, "s", "ONLINE")));
        assertTrue(service.isOnline(1L));
    }

    @Test
    void isOnline_userIsAway_returnsTrue() {
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(presence(1L, "s", "AWAY")));
        assertTrue(service.isOnline(1L));
    }

    @Test
    void isOnline_userIsDnd_returnsFalse() {
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(presence(1L, "s", "DND")));
        assertFalse(service.isOnline(1L));
    }

    @Test
    void isOnline_userIsInvisible_returnsFalse() {
        when(presenceRepository.findByUserId(1L)).thenReturn(List.of(presence(1L, "s", "INVISIBLE")));
        assertFalse(service.isOnline(1L));
    }

    @Test
    void isOnline_noSessions_returnsFalse() {
        when(presenceRepository.findByUserId(99L)).thenReturn(Collections.emptyList());
        assertFalse(service.isOnline(99L));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ping_sessionFound_updatesLastPingAtAndSaves() {
        UserPresence p = presence(1L, "sess-1", "ONLINE");
        when(presenceRepository.findBySessionId("sess-1")).thenReturn(Optional.of(p));
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.ping("sess-1");

        assertNotNull(p.getLastPingAt());
        assertEquals(120L, p.getTtl());
        verify(presenceRepository).save(p);
    }

    @Test
    void ping_sessionNotFound_doesNothing() {
        when(presenceRepository.findBySessionId("ghost")).thenReturn(Optional.empty());

        service.ping("ghost");

        verify(presenceRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cleanStaleSessions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void cleanStaleSessions_staleSessionsExist_deletesThemAndLogs() {
        UserPresence stale = presence(1L, "sess-stale", "ONLINE");
        stale.setLastPingAt(LocalDateTime.now().minusSeconds(120)); // beyond threshold

        UserPresence fresh = presence(2L, "sess-fresh", "ONLINE");
        fresh.setLastPingAt(LocalDateTime.now()); // within threshold

        when(presenceRepository.findAll()).thenReturn(List.of(stale, fresh));

        service.cleanStaleSessions();

        verify(presenceRepository).deleteBySessionId("sess-stale");
        verify(presenceRepository, never()).deleteBySessionId("sess-fresh");
    }

    @Test
    void cleanStaleSessions_noStaleSessions_deletesNothing() {
        UserPresence fresh = presence(1L, "sess-fresh", "ONLINE");
        fresh.setLastPingAt(LocalDateTime.now());

        when(presenceRepository.findAll()).thenReturn(List.of(fresh));

        service.cleanStaleSessions();

        verify(presenceRepository, never()).deleteBySessionId(any());
    }

    @Test
    void cleanStaleSessions_sessionWithNullPingAt_isNotDeleted() {
        UserPresence p = presence(1L, "sess-null", "ONLINE");
        p.setLastPingAt(null);

        when(presenceRepository.findAll()).thenReturn(List.of(p));

        service.cleanStaleSessions();

        verify(presenceRepository, never()).deleteBySessionId(any());
    }

    @Test
    void cleanStaleSessions_emptyRepository_doesNothing() {
        when(presenceRepository.findAll()).thenReturn(Collections.emptyList());

        service.cleanStaleSessions();

        verify(presenceRepository, never()).deleteBySessionId(any());
    }

    @Test
    void cleanStaleSessions_redisDown_firstTime_logsWarning() {
        when(presenceRepository.findAll())
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        // First call — should log warning and set flag
        assertDoesNotThrow(() -> service.cleanStaleSessions());

        // Field should now be true
        Boolean flagAfterFirst = (Boolean) ReflectionTestUtils.getField(service, "redisUnavailableLogged");
        assertTrue(flagAfterFirst);
    }

    @Test
    void cleanStaleSessions_redisDown_secondTime_doesNotLogAgain() {
        when(presenceRepository.findAll())
                .thenThrow(new RedisConnectionFailureException("still down"));

        service.cleanStaleSessions(); // sets flag
        service.cleanStaleSessions(); // should NOT log again (flag already true)

        // No assertion on log, but must not throw
        Boolean flag = (Boolean) ReflectionTestUtils.getField(service, "redisUnavailableLogged");
        assertTrue(flag);
    }

    @Test
    void cleanStaleSessions_redisRestored_resetsFlag() {
        // Simulate Redis was previously unavailable
        ReflectionTestUtils.setField(service, "redisUnavailableLogged", true);

        // Now Redis is back
        when(presenceRepository.findAll()).thenReturn(Collections.emptyList());

        service.cleanStaleSessions();

        Boolean flag = (Boolean) ReflectionTestUtils.getField(service, "redisUnavailableLogged");
        assertFalse(flag);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseStatus — every branch (exercised via setOnline)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void parseStatus_away_lowercase_parsedCorrectly() {
        SetOnlineRequest req = onlineRequest(1L, "s", "away", "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("AWAY", resp.getStatus());
    }

    @Test
    void parseStatus_dnd_mixedCase_parsedCorrectly() {
        SetOnlineRequest req = onlineRequest(1L, "s", "Dnd", "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("DND", resp.getStatus());
    }

    @Test
    void parseStatus_invisible_uppercase_parsedCorrectly() {
        SetOnlineRequest req = onlineRequest(1L, "s", "INVISIBLE", "WEB");
        when(presenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PresenceResponse resp = service.setOnline(req);

        assertEquals("INVISIBLE", resp.getStatus());
    }
}
